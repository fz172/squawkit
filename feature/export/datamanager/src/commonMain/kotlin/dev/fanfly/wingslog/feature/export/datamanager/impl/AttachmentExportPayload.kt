package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Resolved binary attachment ready to be placed in an aircraft export folder.
 */
data class AttachmentExportPayload(
  val attachmentId: String,
  val relativePath: String,
  val bytes: ByteArray,
)
