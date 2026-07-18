package dev.fanfly.wingslog.feature.attachment.datamanager.impl

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.attachment.datamanager.FileTooLargeException
import dev.fanfly.wingslog.feature.attachment.datamanager.ImageCompressor
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker.Companion.MAX_FILE_SIZE_BYTES
import dev.fanfly.wingslog.feature.attachment.datamanager.isCompressiblePhotoMime
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Local-first [AttachmentManager]. See docs/storage/storage_r2_design.md §6.2 + §6.3.
 *
 * `addPickedFile` writes bytes to disk, inserts a `LOCAL_ONLY` blob_object row, and schedules
 * the upload via [uploadScheduler]. No network I/O on the calling coroutine.
 */
@OptIn(ExperimentalTime::class)
class LocalFirstAttachmentManagerImpl(
  private val blobs: LocalBlobStore,
  private val auth: AuthManager,
  private val fileByteReader: FileByteReader,
  private val imageCompressor: ImageCompressor,
  private val uploadScheduler: UploadScheduler? = null,
  private val clock: Clock = Clock.System,
) : AttachmentManager {

  override suspend fun addPickedFile(
    aircraftId: String,
    picked: PickedFile,
    displayName: String,
  ): Attachment {
    val uid = auth.getCurrentUser()?.uid
      ?: error("addPickedFile requires a signed-in user (anonymous or permanent)")
    val rawBytes = fileByteReader.readBytes(picked.uri)
      ?: error("could not read picked file at ${picked.uri}")

    // Single compression point for both the camera and file-picker flows: if this is a photo we
    // re-encode, shrink it to JPEG. The compressor returns null when it declines (not a photo we
    // touch, already small, or a decode failure), and we store the original bytes unchanged.
    val compressed =
      if (isCompressiblePhotoMime(picked.mimeType)) imageCompressor.compressToJpeg(rawBytes)
      else null
    val bytes = compressed ?: rawBytes
    val mimeType = if (compressed != null) "image/jpeg" else picked.mimeType
    val name = if (compressed != null) displayName.withJpegExtension() else displayName

    // Enforce the per-file cap on the *stored* size. Non-photos were already gated on their
    // picked size before we read them; photos are gated here so compression gets to rescue a
    // large image before we reject it.
    if (bytes.size > MAX_FILE_SIZE_BYTES) throw FileTooLargeException(bytes.size.toLong())

    val id = generateRandomId()
    val scope = EntityScope.aircraftChild(
      uid,
      aircraftId
    )
    val ref = blobs.put(
      BlobId(id),
      bytes,
      contentType = mimeType,
      scope = scope
    )
    uploadScheduler?.scheduleUpload(BlobId(id))
    val now = clock.now()
    return Attachment(
      id = id,
      name = name,
      type = mimeType.toAttachmentType(),
      storage_path = "${
        scope.toPath()
          .trim('/')
      }/blobs/$id",
      // Field 5 (download_url) stays empty in R2 — the opener consults LocalBlobStore /
      // re-fetches via Firebase Storage at open time. Reserved in PR 3b.
      download_url = "",
      mime_type = mimeType,
      size_bytes = bytes.size.toLong(),
      created_at = now.toWireInstant(),
      sha256 = ref.sha256,
    )
  }

  /** Swap (or add) a `.jpg` extension so the stored name matches the re-encoded JPEG bytes. */
  private fun String.withJpegExtension(): String {
    if (endsWith(".jpg", ignoreCase = true) || endsWith(".jpeg", ignoreCase = true)) return this
    val dot = lastIndexOf('.')
    val base = if (dot > 0) substring(0, dot) else this
    return "$base.jpg"
  }

  override fun makeLink(
    url: String,
    displayName: String,
  ): Attachment {
    val id = generateRandomId()
    val now = clock.now()
    return Attachment(
      id = id,
      name = displayName,
      type = AttachmentType.ATTACHMENT_TYPE_LINK,
      storage_path = "",
      download_url = "",
      url = url,
      mime_type = "",
      size_bytes = 0L,
      created_at = now.toWireInstant(),
      sha256 = "",
    )
  }

  override suspend fun delete(attachment: Attachment) {
    if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) return
    blobs.delete(BlobId(attachment.id))
  }

  override fun ensureLocal(attachment: Attachment): Flow<DownloadState> {
    val id = BlobId(attachment.id)
    val observed = blobs.observe(id)
      .distinctUntilChanged { a, b -> a?.remoteState == b?.remoteState }
      .onEach { ref ->
        // Schedule download only once the REMOTE_ONLY row actually exists in the DB.
        // Scheduling when the row is null races WorkManager against the reconciler and
        // the worker finds no row, treating it as terminal success and never retrying.
        if (ref?.remoteState == RemoteState.RemoteOnly) uploadScheduler?.scheduleDownload(
          id
        )
      }
      .map { ref ->
        when (ref?.remoteState) {
          RemoteState.Synced,
          RemoteState.LocalOnly,
          RemoteState.Uploading,
            -> DownloadState.Done

          RemoteState.RemoteOnly -> DownloadState.Downloading(0f)
          // null: row not indexed yet (reconciler still running). If sha256 is known the file
          // exists on the server — stay in Downloading so the flow keeps observing until the
          // REMOTE_ONLY row appears and transitions to Synced.
          null -> if (attachment.sha256.isBlank()) DownloadState.Done else DownloadState.Downloading(
            0f
          )
        }
      }
      .transformWhile { state ->
        emit(state)
        state is DownloadState.Downloading
      }

    // The states above only change when the blob_object row changes (reconciler indexing it,
    // the download driver completing it). If that never happens — e.g. the row is never
    // indexed, or the download driver keeps failing and retrying in the background — this flow
    // would otherwise never terminate, leaving the caller's spinner stuck forever with no error.
    // Bound the wait so a stuck download surfaces as a Failed state instead of hanging.
    return flow {
      val completed = withTimeoutOrNull(ENSURE_LOCAL_TIMEOUT_MS.milliseconds) {
        observed.collect { emit(it) }
        true
      }
      if (completed == null) {
        emit(
          DownloadState.Failed(
            Exception("Timed out waiting for ${id.value} to download")
          )
        )
      }
    }
  }

  override fun observeStatus(attachmentId: String): Flow<AttachmentStatus> =
    blobs.observe(BlobId(attachmentId))
      .map { ref ->
        when (ref?.remoteState) {
          null -> AttachmentStatus.RemoteOnly  // unknown id → treat as needing download
          RemoteState.LocalOnly -> AttachmentStatus.LocalOnly
          RemoteState.Uploading -> AttachmentStatus.Uploading(progress = 0f)
          RemoteState.Synced -> AttachmentStatus.Synced
          RemoteState.RemoteOnly -> AttachmentStatus.RemoteOnly
        }
      }

  override fun observeBlobStates(scopePath: String): Flow<Map<String, BlobSyncState>> =
    blobs.observeForScope(scopePath)
      .map { refs ->
        refs.associate { ref -> ref.id.value to ref.toBlobSyncState() }
      }

  override suspend fun retryUpload(id: String) {
    blobs.resetUploadAttempts(BlobId(id))
    uploadScheduler?.scheduleUpload(BlobId(id))
  }

  override suspend fun wipeLocalData(uid: String) {
    uploadScheduler?.cancelAll()
    blobs.wipeForUser(uid)
  }

  private fun BlobRef.toBlobSyncState(): BlobSyncState = when (remoteState) {
    RemoteState.LocalOnly -> if (uploadAttempts > 0) BlobSyncState.UploadFailed else BlobSyncState.PendingUpload
    RemoteState.Uploading -> BlobSyncState.Uploading
    RemoteState.Synced -> BlobSyncState.Synced
    RemoteState.RemoteOnly -> BlobSyncState.RemoteOnly
  }

  private fun String.toAttachmentType(): AttachmentType = when {
    startsWith("image/") -> AttachmentType.ATTACHMENT_TYPE_IMAGE
    this == "application/pdf" -> AttachmentType.ATTACHMENT_TYPE_PDF
    else -> AttachmentType.ATTACHMENT_TYPE_FILE
  }

  companion object {
    private const val ENSURE_LOCAL_TIMEOUT_MS = 30_000L
  }
}
