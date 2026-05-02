package dev.fanfly.wingslog.core.sync

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope

/**
 * Pushes one entity revision to Firestore.
 *
 * The orchestration ([PushWorker]) is platform-agnostic; the actual write needs platform code to
 * convert [ByteArray] → native Blob and to attach a `FieldValue.serverTimestamp` to `updated_at`.
 * Implementations live in `androidMain` / `iosMain` of `core/sync`.
 *
 * The contract is "this revision is the latest the device knows about" — implementations issue an
 * overwrite, never a merge. Batching is an implementation choice and not exposed here so the
 * worker doesn't need to know about [WriteBatch] limits.
 */
interface SyncWriter {

  suspend fun push(write: SyncWrite)
}

/** A single document write the [PushWorker] hands off to a [SyncWriter]. */
data class SyncWrite(
  val kind: CollectionKind,
  val scope: EntityScope,
  val id: String,
  val payload: ByteArray,
  val deleted: Boolean,
  val schema: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SyncWrite) return false
    return kind == other.kind &&
      scope == other.scope &&
      id == other.id &&
      payload.contentEquals(other.payload) &&
      deleted == other.deleted &&
      schema == other.schema
  }

  override fun hashCode(): Int {
    var result = kind.hashCode()
    result = 31 * result + scope.hashCode()
    result = 31 * result + id.hashCode()
    result = 31 * result + payload.contentHashCode()
    result = 31 * result + deleted.hashCode()
    result = 31 * result + schema.hashCode()
    return result
  }
}
