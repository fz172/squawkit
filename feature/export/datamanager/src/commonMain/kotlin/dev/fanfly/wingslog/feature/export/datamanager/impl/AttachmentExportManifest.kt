package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Attachment export result for one thing bundle.
 */
data class AttachmentExportManifest(
  val byAttachmentId: Map<String, AttachmentExportPayload>,
  val notes: List<String>,
)
