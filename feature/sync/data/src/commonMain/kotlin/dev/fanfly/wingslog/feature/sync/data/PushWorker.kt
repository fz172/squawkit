package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.SelectDirtyInScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sync.logging.SyncTelemetry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

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
  // Optional so tests (and any own-tree-only caller) can omit it: without it only the user's own
  // tree drains, preserving pre-sharing behavior. With it, shared aircraft scopes drain too.
  private val storeFactory: EntityStoreFactory? = null,
  private val telemetry: SyncTelemetry = SyncTelemetry.NoOp,
) {

  private val log = Logger.withTag(TAG)

  /**
   * Emits a [SyncFailure] when a push fails with a non-transient error, or `null` after a
   * successful push (so the sink can clear a previously-shown banner). Defaults to no-op; set by
   * [SyncEngine] during wiring.
   */
  var failureSink: (SyncFailure?) -> Unit = {}

  /**
   * Invoked with `(hostUid, aircraftId)` when a push into a shared aircraft's subtree is denied by
   * the rules — i.e. we were revoked. [SyncEngine] wires this to the same local reconcile the read
   * path uses. Defaults to no-op, which leaves the rows dirty (the pre-sharing behavior).
   */
  var sharedScopeRevokedSink: suspend (String, String) -> Unit = { _, _ -> }

  /**
   * Suspends forever, draining `dirty=1` rows as they appear. Cancel the surrounding scope to stop.
   * Suitable to launch from [SyncEngine].
   *
   * **Scoped to a prefix set:** the user's own `users/{uid}/` subtree, plus every shared aircraft's
   * nested-data subtree `users/{hostUid}/aircraft/{acId}/` from the live refs (docs/sharing §5.3).
   * Own-tree scoping keeps account A's undrained writes from being pushed under account B's auth
   * (`PERMISSION_DENIED`) after a device hand-off; the shared prefixes let a member's edits to a
   * shared plane drain to the host's tree. The set is recomputed from the refs store, so a redeemed
   * or revoked share respins the drain loops via [collectLatest].
   */
  suspend fun run(uid: String) {
    scopePrefixes(uid)
      .distinctUntilChanged()
      .collectLatest { prefixes ->
        coroutineScope {
          for (prefix in prefixes) {
            launch { drainLoopForPrefix(prefix, uid) }
          }
        }
      }
  }

  /** Own tree ∪ each live shared aircraft's nested-data subtree. Own-tree only when no store factory. */
  private fun scopePrefixes(uid: String): Flow<Set<String>> {
    val own = scopePrefixFor(uid)
    val refStore =
      storeFactory?.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
        ?: return flowOf(setOf(own))
    return refStore.observeAll(EntityScope.userRoot(uid))
      .map { refs ->
        buildSet {
          add(own)
          for (ref in refs) {
            add(
              sharedAircraftScopePrefix(
                ref.value.host_uid,
                ref.value.aircraft_id
              )
            )
            // The aircraft *doc* sits at the host's root, not inside the per-aircraft subtree, so
            // the prefix above misses it and a co-owner's edit to the aircraft itself would stay
            // dirty forever. This exact-match scope (no trailing %) is the doc's own row. The only
            // rows we ever hold at a host's root are the shared aircraft docs — we never hydrate a
            // host's technicians or user info — so it grants no reach beyond them.
            add(hostRootScope(ref.value.host_uid))
          }
        }
      }
  }

  private suspend fun drainLoopForPrefix(prefix: String, uid: String) {
    db.schemaQueries.countDirtyInScope(prefix)
      .asFlow()
      .mapToOne(ioContext)
      .map { it > 0L }
      .distinctUntilChanged()
      .filter { it }
      .collect { drain(prefix, uid) }
  }

  private suspend fun drain(scopePrefix: String, uid: String) {
    while (true) {
      val rows = db.schemaQueries
        .selectDirtyInScope(
          scopePrefix = scopePrefix,
          limit = DRAIN_PAGE
        )
        .awaitAsList()
      if (rows.isEmpty()) return
      for (row in rows) {
        val ok = pushOne(row, uid)
        if (!ok) {
          // Stop draining this tick; the row stays dirty and the next countDirty>0 edge or
          // an external retry kicks the next attempt. Avoids hot-looping on a hard failure.
          return
        }
      }
    }
  }

  private suspend fun pushOne(row: DirtyRow, uid: String): Boolean =
    runCatching {
      writer.push(
        SyncWrite(
          kind = row.collection,
          scope = EntityScope(parseScopePath(row.scope_path)),
          id = row.id,
          payload = row.payload,
          deleted = row.deleted,
          schema = row.collection.schemaName,
          writerUid = uid,
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
      if (isPermissionDenied(e)) {
        val shared = sharedAircraftIn(row, uid)
        telemetry.permissionDeniedWrite(sharedScope = shared != null)
        if (shared != null) {
          // We were revoked while this edit sat in the queue, and the push beat the ref tombstone to
          // us. This is the write-side twin of the §5.4 read race: reconcile locally rather than
          // accuse the user of an expired session, and let the janitor purge the scope — which drops
          // these rows, so we don't retry a write we will never be allowed to make.
          val (hostUid, aircraftId) = shared
          log.i { "PERMISSION_DENIED pushing to shared aircraft $aircraftId; treating as revoked (§5.4)" }
          sharedScopeRevokedSink(hostUid, aircraftId)
          return@getOrElse false
        }
      }
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
  path.trim('/')
    .split('/')
    .filter { it.isNotEmpty() }

private typealias DirtyRow = SelectDirtyInScope

/** Builds the SQL `LIKE` prefix that matches every scope under `users/{uid}/`. */
private fun scopePrefixFor(uid: String): String = "/users/$uid/%"

/**
 * Exact `LIKE` scope (no wildcard) for a host's root, where the shared aircraft *doc* rows sit —
 * `users/{hostUid}/` holds the doc; `users/{hostUid}/aircraft/{acId}/` holds its nested data.
 */
private fun hostRootScope(hostUid: String): String = "/users/$hostUid/"

/** `LIKE` prefix matching a shared aircraft's nested-data subtree in the host's tree. */
private fun sharedAircraftScopePrefix(
  hostUid: String,
  aircraftId: String
): String =
  "/users/$hostUid/aircraft/$aircraftId/%"

/**
 * `(hostUid, aircraftId)` when [row] belongs to a shared aircraft in *someone else's* tree, else
 * null. Anything under our own `/users/{uid}/...` is never a share, however deep it sits.
 *
 * Two shapes qualify, because a shared aircraft straddles two scopes: its nested data lives at
 * `/users/{host}/aircraft/{acId}/`, while the aircraft doc itself is a row *at* `/users/{host}/`,
 * where the aircraft id is the row id rather than part of the path.
 */
private fun sharedAircraftIn(
  row: DirtyRow,
  uid: String
): Pair<String, String>? {
  val parts = parseScopePath(row.scope_path)
  if (parts.size < 2 || parts[0] != "users") return null
  val hostUid = parts[1]
  if (hostUid == uid) return null

  return when {
    parts.size >= 4 && parts[2] == "aircraft" -> hostUid to parts[3]
    parts.size == 2 && row.collection == CollectionKind.Aircraft -> hostUid to row.id
    else -> null
  }
}
