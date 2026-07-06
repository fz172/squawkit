package dev.fanfly.wingslog.feature.attachment.datamanager

sealed class OpenState {
  /** File download is in progress (or system handoff is pending). */
  object Downloading : OpenState()

  /** System viewer / browser has been handed off successfully. */
  object Done : OpenState()

  data class Failed(val error: Throwable) : OpenState()
}

/** Attachment was written by an R1 build (sha256 empty, no blob_object row). Re-attach to restore access. */
class LegacyAttachment :
  Exception("Attachment added in an older version; re-attach the file to restore access.")
