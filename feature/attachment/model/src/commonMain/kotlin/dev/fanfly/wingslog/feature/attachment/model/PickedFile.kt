package dev.fanfly.wingslog.feature.attachment.model

/**
 * A file returned by the platform file picker, before its bytes are read into the local blob
 * store. [uri] is platform-specific opaque (Android content URI, iOS file path).
 */
data class PickedFile(
  val uri: String,
  val name: String,
  val mimeType: String,
  val sizeBytes: Long,
)
