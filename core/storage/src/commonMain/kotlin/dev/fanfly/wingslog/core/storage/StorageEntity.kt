package dev.fanfly.wingslog.core.storage

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One row of [EntityStore] data after the bytes have been decoded by an [EntityCodec].
 *
 * @property id Caller-assigned identifier — opaque to the store, unique within (kind, scope).
 * @property value The decoded domain object.
 * @property updatedAt Wall-clock timestamp the row was last written locally. For UI display only;
 *   sync ordering uses Firestore server timestamps stored separately. See storage_r1_design.md §5.3.
 */
@OptIn(ExperimentalTime::class)
data class StorageEntity<T : Any>(
  val id: String,
  val value: T,
  val updatedAt: Instant,
)
