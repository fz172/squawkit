package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.SelectDirtyInScope
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
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  private val log = Logger.withTag(TAG)

  /**
   * Emits a [SyncFailure] when a push fails with a non-transient error, or `null` after a
   * successful push (so the sink can clear a previously-shown banner). Defaults to no-op; set by
   * [SyncEngine] during wiring.
   */
  var failureSink: (SyncFailure?) -> Unit = {}

  /**
   * Suspends forever, draining `dirty=1` rows belonging to [uid] as they appear. Cancel the
   * surrounding scope to stop. Suitable to launch from [SyncEngine].
   *
   * **Why scoped to [uid]:** when account A's writes haven't drained before A signs out, those
   * rows stay `dirty=1` in the local table. If account B then signs in on the same device,
   * pushing A's rows under B's auth would `PERMISSION_DENIED` against `/users/A/...`. Filtering
   * the dirty queue by the current user's `users/{uid}/` prefix keeps each account's pending
   * writes durable and isolated until that account signs back in.
   */
  suspend fun run(uid: String) {
    val prefix = scopePrefixFor(uid)
    db.schemaQueries.countDirtyInScope(prefix)
      .asFlow()
      .mapToOne(ioContext)
      .map { it > 0L }
      .distinctUntilChanged()
      .filter { it }
      .collect { drain(prefix) }
  }

  private suspend fun drain(scopePrefix: String) {
    while (true) {
      val rows = db.schemaQueries
        .selectDirtyInScope(
          scopePrefix = scopePrefix,
          limit = DRAIN_PAGE
        )
        .awaitAsList()
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
    writeLock.withLock {
      db.schemaQueries.clearDirty(
        row.collection,
        row.scope_path,
        row.id
      )
    }
    failureSink(null) // success — clear any previously-surfaced push failure.
    true
  }.getOrElse { e ->
    val classified = classifyPushFailure(e)
    if (classified != null) {
      log.w(e) { "push failed for ${row.collection.wireName}/${row.id}: ${classified.message}" }
    } else {
      log.i { "push transient failure for ${row.collection.wireName}/${row.id}; will retry on next notification" }
    }
    failureSink(classified)
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

private typealias DirtyRow = SelectDirtyInScope

/** Builds the SQL `LIKE` prefix that matches every scope under `users/{uid}/`. */
private fun scopePrefixFor(uid: String): String = "/users/$uid/%"
