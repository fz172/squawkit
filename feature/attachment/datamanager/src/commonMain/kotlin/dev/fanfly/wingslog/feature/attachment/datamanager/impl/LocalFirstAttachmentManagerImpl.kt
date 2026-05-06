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
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Local-first [AttachmentManager]. See docs/storage_r2_design.md §6.2 + §6.3.
 *
 * `addPickedFile` is intentionally synchronous from the form's perspective: it reads bytes,
 * writes to disk, inserts a `LOCAL_ONLY` blob_object row, and returns the populated proto. No
 * network I/O. The `UploadScheduler` (PR 5) will pick the row up out-of-band.
 */
@OptIn(ExperimentalTime::class)
class LocalFirstAttachmentManagerImpl(
  private val blobs: LocalBlobStore,
  private val auth: AuthManager,
  private val fileByteReader: FileByteReader,
  private val clock: Clock = Clock.System,
) : AttachmentManager {

  override suspend fun addPickedFile(picked: PickedFile, displayName: String): Attachment {
    val uid = auth.getCurrentUser()?.uid
      ?: error("addPickedFile requires a signed-in user (anonymous or permanent)")
    val bytes = fileByteReader.readBytes(picked.uri)
      ?: error("could not read picked file at ${picked.uri}")
    val id = generateRandomId()
    val scope = EntityScope.userRoot(uid)
    val storagePath = "users/$uid/blobs/$id"
    val ref = blobs.put(BlobId(id), bytes, contentType = picked.mimeType, scope = scope)
    val now = clock.now()
    return Attachment(
      id = id,
      name = displayName,
      type = picked.mimeType.toAttachmentType(),
      storage_path = storagePath,
      // Field 5 (download_url) stays empty in R2 — the opener consults LocalBlobStore /
      // re-fetches via Firebase Storage at open time. Reserved in PR 3b.
      download_url = "",
      mime_type = picked.mimeType,
      size_bytes = picked.sizeBytes,
      created_at = now.toWireInstant(),
      sha256 = ref.sha256,
    )
  }

  override fun makeLink(url: String, displayName: String): Attachment {
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
    // Real download wiring lands in PR 5 (BlobDownloadDriver + UploadScheduler). For now we
    // only handle the trivial "already local" case so callers can integrate against the
    // contract today.
    return flowOf(DownloadState.Done)
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

  private fun String.toAttachmentType(): AttachmentType = when {
    startsWith("image/") -> AttachmentType.ATTACHMENT_TYPE_IMAGE
    this == "application/pdf" -> AttachmentType.ATTACHMENT_TYPE_PDF
    else -> AttachmentType.ATTACHMENT_TYPE_FILE
  }
}
