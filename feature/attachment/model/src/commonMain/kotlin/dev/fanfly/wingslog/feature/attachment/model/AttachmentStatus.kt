package dev.fanfly.wingslog.feature.attachment.model

/**
 * Per-attachment upload-pipeline state surfaced to UI. See docs/storage_r2_design.md §6.2.
 *
 * The viewing layer renders these as a small badge next to each attachment row:
 * - [LocalOnly] / [Uploading]: cloud-with-up-arrow icon
 * - [Synced]: plain icon (no badge)
 * - [RemoteOnly]: download icon
 * - [Failed]: error icon with retry affordance
 */
sealed class AttachmentStatus {
  data object LocalOnly : AttachmentStatus()
  data class Uploading(val progress: Float) : AttachmentStatus()
  data object Synced : AttachmentStatus()
  data object RemoteOnly : AttachmentStatus()
  data class Failed(val error: Throwable) : AttachmentStatus()
}
