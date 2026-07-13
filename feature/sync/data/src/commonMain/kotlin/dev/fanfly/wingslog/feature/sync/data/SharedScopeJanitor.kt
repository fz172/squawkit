package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.awaitAsList
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
) {
  private val log = Logger.withTag(TAG)

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
  }

  companion object {
    private const val TAG = "SharedScopeJanitor"
  }
}

/** `/users/{hostUid}/` → hostUid, or null if [scopePath] isn't a user-root path. */
private fun hostUidFromRoot(scopePath: String): String? {
  val segments = scopePath.trim('/')
    .split('/')
  return if (segments.size == 2 && segments[0] == "users") segments[1] else null
}
