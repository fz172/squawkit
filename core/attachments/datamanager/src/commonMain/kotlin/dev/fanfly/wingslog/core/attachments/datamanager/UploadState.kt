package dev.fanfly.wingslog.core.attachments.datamanager

import dev.fanfly.wingslog.aircraft.Attachment

sealed class UploadState {
  /** Upload is in flight. [progress] is 0–1 (indeterminate in V1, always 0f). */
  data class Uploading(val progress: Float = 0f) : UploadState()
  data class Done(val attachment: Attachment) : UploadState()
  data class Failed(val error: Throwable) : UploadState()
}
