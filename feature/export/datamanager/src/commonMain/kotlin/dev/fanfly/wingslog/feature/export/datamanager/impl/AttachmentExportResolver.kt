package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.BlobFilesystem
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import kotlinx.coroutines.flow.first

/**
 * Resolves non-link attachments into binary payloads that can be embedded in an export ZIP.
 */
class AttachmentExportResolver(
  private val attachmentManager: AttachmentManager,
  private val localBlobStore: LocalBlobStore,
  private val blobFilesystem: BlobFilesystem,
) {

  /**
   * Downloads missing binaries when possible and returns the local payloads for [bundle].
   */
  suspend fun resolve(bundle: AircraftBundle): AttachmentExportManifest {
    val payloads = linkedMapOf<String, AttachmentExportPayload>()
    val notes = mutableListOf<String>()

    bundle.exportedAttachments()
      .filter { attachment -> attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK }
      .forEach { attachment ->
        if (attachment.id.isBlank() || payloads.containsKey(attachment.id)) return@forEach

        val result = runCatching {
          attachmentManager.ensureLocal(attachment)
            .first { state -> state !is DownloadState.Downloading }
        }
        val state = result.getOrNull()
        if (state is DownloadState.Failed || result.isFailure) {
          notes += "Attachment ${attachment.id} could not be downloaded."
          return@forEach
        }

        val blobId = BlobId(attachment.id)
        val ref = localBlobStore.get(blobId)
        if (ref == null) {
          notes += "Attachment ${attachment.id} has no local blob record."
          return@forEach
        }

        val bytes =
          runCatching { blobFilesystem.read(ref.relativePath) }.getOrNull()
        if (bytes == null) {
          notes += "Attachment ${attachment.id} local file could not be read."
          return@forEach
        }

        payloads[attachment.id] = AttachmentExportPayload(
          attachmentId = attachment.id,
          relativePath = attachment.exportRelativePath(),
          bytes = bytes,
        )
      }

    return AttachmentExportManifest(
      byAttachmentId = payloads,
      notes = notes,
    )
  }

  private fun AircraftBundle.exportedAttachments(): List<Attachment> =
    logs.flatMap { it.attachments } +
      tasks.flatMap { it.attachments } +
      squawks.flatMap { it.attachments }

  private fun Attachment.exportRelativePath(): String {
    val shortId = id.take(4)
      .ifBlank { "file" }
    val fileName = name
      .ifBlank { "$shortId.${mime_type.extension()}" }
      .sanitizeAttachmentFileName()
    return "attachments/${shortId}_$fileName"
  }

  private fun String.extension(): String =
    when (substringAfter('/', "").substringBefore(';')) {
      "jpeg" -> "jpg"
      "png" -> "png"
      "pdf" -> "pdf"
      "plain" -> "txt"
      else -> "bin"
    }

  private fun String.sanitizeAttachmentFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
      .ifBlank { "attachment.bin" }
}
