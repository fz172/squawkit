package dev.fanfly.wingslog.feature.attachment.model

/**
 * What an attachment row shows for a DATA_LOG reference: the referenced record's product and
 * duration, and its raw file's blob state, which gates the tap (design §9.1). A reference whose
 * record is gone has no entry and renders as *Removed*.
 */
data class DataLogRowInfo(
  val product: String,
  val durationSeconds: Int,
  val syncState: BlobSyncState?,
)
