package dev.fanfly.wingslog.core.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Watches the `entity` table for `dirty=1` rows and pushes them to Firestore via [SyncWriter].
 *
 * Drains each dirty backlog one row at a time within a single flow collection, marking each row
 * `dirty=0` only after its write succeeds. A failed write leaves the row dirty so the next dirty
 * notification picks it up. Cross-document atomicity is unnecessary — local already holds the
 * complete picture and per-document LWW resolves any ordering server-side. Batching can be added
 * later inside the [SyncWriter] implementation without changing this class.
 *
 * `clearDirty` does **not** stamp `remote_updated_at`. The pull listener will receive the echoed
 * server-stamped doc moments later and call `setRemoteUpdatedAt` then. Until that arrives the row
 * sits in `dirty=0, remote_updated_at=null`, which the conflict comparator treats as "any later
 * remote write wins" — the conservative answer.
 */
class PushWorker(
  private val db: WingsLogDatabase,
  private val writer: SyncWriter,
  private val ioContext: CoroutineContext,
) {

  private val log = Logger.withTag(TAG)

  /**
   * Suspends forever, draining `dirty=1` rows as they appear. Cancel the surrounding scope to
   * stop. Suitable to launch from [SyncEngine].
   */
  suspend fun run() {
    db.schemaQueries.countDirty()
      .asFlow()
      .mapToOne(ioContext)
      .map { it > 0L }
      .distinctUntilChanged()
      .filter { it }
      .collect { drain() }
  }

  private suspend fun drain() {
    while (true) {
      val rows = db.schemaQueries.selectDirty(limit = DRAIN_PAGE).executeAsList()
      if (rows.isEmpty()) return
      for (row in rows) {
        val ok = pushOne(row)
        if (!ok) {
          // Stop draining this tick; the row stays dirty and the next countDirty>0 edge or
          // an external retry kicks the next attempt. Avoids hot-looping on a hard failure.
          return
        }
      }
    }
  }

  private suspend fun pushOne(row: DirtyRow): Boolean = runCatching {
    writer.push(
      SyncWrite(
        kind = row.collection,
        scope = EntityScope(parseScopePath(row.scope_path)),
        id = row.id,
        payload = row.payload,
        deleted = row.deleted,
        schema = row.collection.schemaName,
      ),
    )
    db.schemaQueries.clearDirty(row.collection, row.scope_path, row.id)
    true
  }.getOrElse { e ->
    log.w(e) { "push failed for ${row.collection.wireName}/${row.id}; will retry" }
    false
  }

  companion object {
    private const val TAG = "PushWorker"

    /**
     * Cap on rows pulled from the dirty index per page. Keeps memory bounded on a large backlog
     * (e.g. first sync after a long offline stretch) while still letting drain make progress in a
     * single tick when the queue is small.
     */
    const val DRAIN_PAGE: Long = 200
  }
}

/** Reverse of [EntityScope.toPath]: `"/users/u1/aircraft/ac1/"` → `["users", "u1", "aircraft", "ac1"]`. */
private fun parseScopePath(path: String): List<String> =
  path.trim('/').split('/').filter { it.isNotEmpty() }

private typealias DirtyRow = dev.fanfly.wingslog.core.storage.db.SelectDirty
