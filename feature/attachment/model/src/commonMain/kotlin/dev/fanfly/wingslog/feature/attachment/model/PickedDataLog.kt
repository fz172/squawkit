package dev.fanfly.wingslog.feature.attachment.model

import dev.fanfly.wingslog.id.DataLogId

/** A data log chosen in the attachment picker; [displayName] becomes the attachment's name. */
data class PickedDataLog(
  val id: DataLogId,
  val displayName: String,
)
