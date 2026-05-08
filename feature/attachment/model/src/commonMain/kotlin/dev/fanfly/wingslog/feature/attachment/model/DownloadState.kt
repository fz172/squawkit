package dev.fanfly.wingslog.feature.attachment.model

/**
 * Emitted by `AttachmentManager.ensureLocal` while a REMOTE_ONLY blob is being fetched.
 * See docs/storage_r2_design.md §6.2.
 */
sealed class DownloadState {
  data class Downloading(val progress: Float) : DownloadState()
  data object Done : DownloadState()
  data class Failed(val error: Throwable) : DownloadState()
}
