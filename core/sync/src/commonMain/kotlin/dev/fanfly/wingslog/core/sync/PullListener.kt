package dev.fanfly.wingslog.core.sync

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Applies remote document updates from a single Firestore subscription to the local `entity`
 * table.
 *
 * The Firestore plumbing — opening the snapshot listener, decoding `Blob` to `ByteArray`,
 * converting `FIRTimestamp` to epoch-ms — lives in platform code; this class is the
 * platform-agnostic comparator and writer. A platform glue layer feeds it [RemoteEntity] values.
 *
 * ### Comparator (the "four cases")
 * Given a remote entity for `(kind, scope, id)`:
 *
 * 1. **No local row** — adopt the remote: `INSERT` with `dirty=0` and `remote_updated_at = remoteTs`.
 * 2. **Local row is dirty** — local has an unpushed change. Drop the remote on the floor; the
 *    PushWorker will publish our change, the server-side LWW will pick a winner, and the next
 *    snapshot tick will deliver that winner here.
 * 3. **Remote is strictly newer** (`remoteTs > local.remote_updated_at ?: 0`) — overwrite local
 *    with the remote, leaving `dirty=0`.
 * 4. **Otherwise** — local already has this revision (or a newer one we just pushed). No-op.
 *
 * After applying (or even no-op'ing), the per-(uid, kind, scope) cursor's `last_seen_remote`
 * advances to `max(prev, remoteTs)` so a subsequent `where("updated_at", ">", cursor)` query skips
 * docs we've already seen. Cursor advancement is the [SyncCursorStore]'s job; this class only
 * returns the timestamp it observed.
 */
class PullListener(
  private val kind: CollectionKind,
  private val scope: EntityScope,
  private val db: WingsLogDatabase,
) {

  private val log = Logger.withTag(TAG)

  /**
   * Apply one remote entity. Returns the `remoteTs` so the caller can advance its cursor.
   *
   * Wraps the read+write in a single SQLDelight transaction so a concurrent local `put` can't slip
   * a row into a state the comparator didn't see.
   */
  fun apply(remote: RemoteEntity): Long {
    db.transaction {
      val local =
        db.schemaQueries.selectOneForSync(kind, scope.toPath(), remote.id).executeAsOneOrNull()
      when {
        local == null -> upsertFromRemote(remote)
        local.dirty -> {
          log.v { "skipping remote ${kind.wireName}/${remote.id}: local dirty, will reconcile via push echo" }
        }
        remote.remoteTsMs > (local.remote_updated_at ?: 0L) -> upsertFromRemote(remote)
        else -> {
          // Already up to date — happens on our own push echo or a duplicate snapshot tick.
        }
      }
    }
    return remote.remoteTsMs
  }

  private fun upsertFromRemote(remote: RemoteEntity) {
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope.toPath(),
      id = remote.id,
      payload = remote.payload,
      payload_schema = kind.schemaName,
      updated_at = remote.remoteTsMs,
      remote_updated_at = remote.remoteTsMs,
      dirty = false,
      deleted = remote.deleted,
    )
  }

  companion object {
    private const val TAG = "PullListener"
  }
}

/**
 * Platform-decoded shape of one Firestore document arriving via a snapshot listener or a one-shot
 * `get()`. Keeps [PullListener] free of Firebase types.
 *
 * @param remoteTsMs server-stamped `updated_at` field, in epoch ms. Always populated — a doc with
 *   no server timestamp yet (e.g. a not-yet-acked local write echoed back) should be filtered out
 *   in the platform layer rather than passed here.
 */
data class RemoteEntity(
  val id: String,
  val payload: ByteArray,
  val deleted: Boolean,
  val remoteTsMs: Long,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RemoteEntity) return false
    return id == other.id &&
      deleted == other.deleted &&
      remoteTsMs == other.remoteTsMs &&
      payload.contentEquals(other.payload)
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + deleted.hashCode()
    result = 31 * result + remoteTsMs.hashCode()
    result = 31 * result + payload.contentHashCode()
    return result
  }
}
