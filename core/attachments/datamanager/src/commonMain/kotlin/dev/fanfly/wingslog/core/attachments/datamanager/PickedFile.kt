package dev.fanfly.wingslog.core.attachments.datamanager

/**
 * A file returned by the platform file picker, before it is uploaded.
 * [uri] is a platform-specific opaque string (content URI on Android, file path on iOS).
 */
data class PickedFile(
  val uri: String,
  val name: String,
  val mimeType: String,
  val sizeBytes: Long,
)
