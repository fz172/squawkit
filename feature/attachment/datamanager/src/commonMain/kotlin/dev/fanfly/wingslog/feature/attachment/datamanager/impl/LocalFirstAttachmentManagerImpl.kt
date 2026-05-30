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
import dev.fanfly.wingslog.feature.attachment.datamanager.BlobRef
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.datamanager.UploadScheduler
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile

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
    val bytes = fileByteReader.readBytes(picked.uri)
      ?: error("could not read picked file at ${picked.uri}")
    val id = generateRandomId()
    val scope = EntityScope.aircraftChild(
      uid,
      aircraftId
    )
    val ref = blobs.put(
      BlobId(id),
      bytes,
      contentType = picked.mimeType,
      scope = scope
    )
    uploadScheduler?.scheduleUpload(BlobId(id))
    val now = clock.now()
    return Attachment(
      id = id,
      name = displayName,
      type = picked.mimeType.toAttachmentType(),
      storage_path = "${scope.toPath().trim('/')}/blobs/$id",
      // Field 5 (download_url) stays empty in R2 — the opener consults LocalBlobStore /
      // re-fetches via Firebase Storage at open time. Reserved in PR 3b.
      download_url = "",
      mime_type = picked.mimeType,
      size_bytes = picked.sizeBytes,
      created_at = now.toWireInstant(),
      sha256 = ref.sha256,
    )
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
    return blobs.observe(id)
      .distinctUntilChanged { a, b -> a?.remoteState == b?.remoteState }
      .onEach { ref ->
        // Schedule download only once the REMOTE_ONLY row actually exists in the DB.
        // Scheduling when the row is null races WorkManager against the reconciler and
        // the worker finds no row, treating it as terminal success and never retrying.
        if (ref?.remoteState == RemoteState.RemoteOnly) uploadScheduler?.scheduleDownload(id)
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
          null -> if (attachment.sha256.isBlank()) DownloadState.Done else DownloadState.Downloading(0f)
        }
      }
      .transformWhile { state ->
        emit(state)
        state is DownloadState.Downloading
      }
  }

  override fun observeStatus(attachmentId: String): Flow<AttachmentStatus> =
    blobs.observe(BlobId(attachmentId)).map { ref ->
      when (ref?.remoteState) {
        null -> AttachmentStatus.RemoteOnly  // unknown id → treat as needing download
        RemoteState.LocalOnly -> AttachmentStatus.LocalOnly
        RemoteState.Uploading -> AttachmentStatus.Uploading(progress = 0f)
        RemoteState.Synced -> AttachmentStatus.Synced
        RemoteState.RemoteOnly -> AttachmentStatus.RemoteOnly
      }
    }

  override fun observeBlobStates(scopePath: String): Flow<Map<String, BlobSyncState>> =
    blobs.observeForScope(scopePath).map { refs ->
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
}
