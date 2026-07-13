package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.storage.EntityStore
import kotlinx.coroutines.flow.first
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Purges a member's local copy of a shared aircraft once its share ends (revoke / leave / aircraft
 * delete): a shared aircraft with local data but no live ref is stale and is removed.
 *
 * Deletes are **hard SQL deletes**, not entity tombstones — this is another account's data the
 * member was only granted a view of, so it was never ours to tombstone (tombstoning would try to
 * push a deletion into the host's tree, which the member no longer has access to). The host-scoped
 * aircraft-doc cursor is left as-is: harmless, since the doc-level pull doesn't use a watermark.
 *
 * Runs on every refs change and at engine start (docs/sharing §5.4).
 */
class SharedScopeJanitor(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock,
  /** Names the aircraft for the discarded-changes notice, before it is purged and unnameable. */
  private val aircraftStore: EntityStore<Aircraft>? = null,
) {
  private val log = Logger.withTag(TAG)

  /**
   * Reports work that was thrown away: unsynced edits to a share that ended (PRD D3). Set by
   * [SyncEngine]; the purge itself proceeds either way.
   */
  var noticeSink: (SyncNotice) -> Unit = {}

  /**
   * [liveShares] is the set of `(hostUid, aircraftId)` the member is still a member of (from the
   * refs store). Any shared aircraft present locally but absent from [liveShares] is purged.
   */
  suspend fun purgeRevoked(
    memberUid: String,
    liveShares: Set<Pair<String, String>>
  ) {
    val ownRoot = EntityScope.userRoot(memberUid)
      .toPath()
    val localShared = db.schemaQueries
      .selectScopeAndIdForCollection(CollectionKind.Aircraft)
      .awaitAsList()
      .mapNotNull { row ->
        if (row.scope_path == ownRoot) return@mapNotNull null // the member's own aircraft
        hostUidFromRoot(row.scope_path)?.let { host -> host to row.id }
      }
      .toSet()

    val toPurge = localShared - liveShares
    if (toPurge.isEmpty()) return

    // What we are about to destroy has to be counted and named *before* it is gone, not after.
    val discarded = toPurge.associateWith { (hostUid, aircraftId) ->
      DiscardedWork(
        label = aircraftLabel(hostUid, aircraftId),
        count = dirtyCountFor(hostUid, aircraftId),
      )
    }

    writeLock.withLock {
      for ((hostUid, aircraftId) in toPurge) {
        val nestedPrefix = "/users/$hostUid/aircraft/$aircraftId/%"
        db.schemaQueries.deleteEntitiesInScopePrefix(nestedPrefix) // logs/tasks/squawks/overview
        db.schemaQueries.deleteEntity(
          CollectionKind.Aircraft,
          EntityScope.userRoot(hostUid)
            .toPath(),
          aircraftId, // the aircraft doc itself
        )
        db.schemaQueries.deleteCursorsInScopePrefix(nestedPrefix)
        log.i { "purged revoked shared aircraft $aircraftId (host $hostUid)" }
      }
    }

    // Only after the purge has actually happened — telling someone their work is gone, and then
    // failing to remove it, would be worse than either outcome alone.
    for ((_, work) in discarded) {
      if (work.count > 0) {
        log.i { "discarded ${work.count} unsynced change(s) to ${work.label}" }
        noticeSink(SyncNotice.ChangesDiscarded(work.label, work.count))
      }
    }
  }

  private data class DiscardedWork(val label: String, val count: Int)

  /**
   * Unsynced rows in this aircraft's scopes. Both halves count: its nested records
   * (`/users/{host}/aircraft/{acId}/%`) and the aircraft doc itself, which is a row *at* the host's
   * root — a co-owner's edit to the aircraft lives there, and missing it would under-report the loss.
   */
  private suspend fun dirtyCountFor(hostUid: String, aircraftId: String): Int {
    val nested = db.schemaQueries
      .countDirtyInScope("/users/$hostUid/aircraft/$aircraftId/%")
      .awaitAsOne()
    val doc = db.schemaQueries
      .selectOne(
        CollectionKind.Aircraft,
        EntityScope.userRoot(hostUid).toPath(),
        aircraftId,
      )
      .awaitAsOneOrNull()
    val docDirty = if (doc != null && isDirty(hostUid, aircraftId)) 1 else 0
    return nested.toInt() + docDirty
  }

  private suspend fun isDirty(hostUid: String, aircraftId: String): Boolean =
    db.schemaQueries
      .selectDirtyInScope(
        scopePrefix = EntityScope.userRoot(hostUid).toPath(),
        limit = DIRTY_PROBE_LIMIT,
      )
      .awaitAsList()
      .any { it.collection == CollectionKind.Aircraft && it.id == aircraftId }

  /** Tail number if we can still read it; a generic label otherwise (the notice must still be sent). */
  private suspend fun aircraftLabel(hostUid: String, aircraftId: String): String {
    val tail = aircraftStore
      ?.observe(aircraftId, EntityScope.userRoot(hostUid))
      ?.first()
      ?.value
      ?.tail_number
      ?.takeIf { it.isNotBlank() }
    return tail ?: "a shared aircraft"
  }

  companion object {
    private const val TAG = "SharedScopeJanitor"

    /** Host-root dirty rows are only ever shared aircraft docs, so a small page covers them all. */
    private const val DIRTY_PROBE_LIMIT = 200L
  }
}

/** `/users/{hostUid}/` → hostUid, or null if [scopePath] isn't a user-root path. */
private fun hostUidFromRoot(scopePath: String): String? {
  val segments = scopePath.trim('/')
    .split('/')
  return if (segments.size == 2 && segments[0] == "users") segments[1] else null
}
