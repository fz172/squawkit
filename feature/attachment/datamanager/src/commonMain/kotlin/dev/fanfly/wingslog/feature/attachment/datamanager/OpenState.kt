package dev.fanfly.wingslog.feature.attachment.datamanager

sealed class OpenState {
  /** File download is in progress (or system handoff is pending). */
  object Downloading : OpenState()

  /** System viewer / browser has been handed off successfully. */
  object Done : OpenState()

  data class Failed(val error: Throwable) : OpenState()
}
