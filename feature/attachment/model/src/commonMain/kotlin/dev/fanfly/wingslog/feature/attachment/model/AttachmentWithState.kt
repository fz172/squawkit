package dev.fanfly.wingslog.feature.attachment.model

import dev.fanfly.wingslog.aircraft.Attachment

data class AttachmentWithState(
  val attachment: Attachment,
  val syncState: BlobSyncState?,  // null = link type (no blob lifecycle)
)
