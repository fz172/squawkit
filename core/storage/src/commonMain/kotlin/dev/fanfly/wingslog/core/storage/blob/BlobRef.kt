package dev.fanfly.wingslog.core.storage.blob

import dev.fanfly.wingslog.core.storage.EntityScope
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Caller-facing projection of one `blob_object` row. See docs/storage/storage_r2_design.md §6.1.
 */
@OptIn(ExperimentalTime::class)
data class BlobRef(
  val id: BlobId,
  val scope: EntityScope,
  val relativePath: String,
  val sizeBytes: Long,
  val sha256: String,
  val contentType: String?,
  val remoteState: RemoteState,
  val remotePath: String?,
  val uploadAttempts: Long,
  val deleted: Boolean,
  val updatedAt: Instant,
)
