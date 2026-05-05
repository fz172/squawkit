package dev.fanfly.wingslog.feature.sync.data

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Bulk-pulls a `(kind, scope)` collection from Firestore on first sign-in (or after a long
 * offline stretch) and writes every doc into the local `entity` table.
 *
 * Idempotent: every row is `INSERT OR REPLACE` keyed on `(collection, scope_path, id)`, so a
 * partially-completed previous attempt re-runs cleanly. We therefore don't need resumable
 * chunking in R1.
 *
 * On success the cursor's `last_seen_remote` advances to the max `remoteTsMs` seen, the cursor is
 * marked `hydrated = true`, and `failed_attempts` resets to 0. On failure we increment
 * `failed_attempts` so [SyncEngine] can compute a per-(kind, scope) backoff before retrying — a
 * flaky read for one aircraft's logs doesn't block hydration of another aircraft.
 */
class HydrationRunner(
  private val db: WingsLogDatabase,
  private val fetcher: RemoteFetcher,
  private val cursors: SyncCursorStore,
) {

  private val log = Logger.withTag(TAG)

  /**
   * Returns `true` on a successful run, `false` on any failure (failure already recorded in the
   * cursor; the caller decides whether to schedule a retry).
   */
  suspend fun runFor(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
  ): Boolean {
    return runCatching {
      val docs = fetcher.fetchAll(
        kind,
        scope
      )
      val maxTs = applyAndComputeMaxTs(
        kind,
        scope,
        docs
      )
      cursors.markHydrated(
        uid = uid,
        kind = kind,
        scope = scope,
        lastSeenRemote = maxTs
      )
      log.i { "hydrated ${kind.wireName} ${scope.toPath()}: ${docs.size} doc(s)" }
      true
    }.getOrElse { e ->
      log.w(e) { "hydration failed for ${kind.wireName} ${scope.toPath()}; recording for backoff" }
      cursors.recordFailure(
        uid,
        kind,
        scope
      )
      false
    }
  }

  private fun applyAndComputeMaxTs(
    kind: CollectionKind,
    scope: EntityScope,
    docs: List<RemoteEntity>,
  ): Long? {
    if (docs.isEmpty()) return null
    var maxTs = Long.MIN_VALUE
    db.transaction {
      for (doc in docs) {
        db.schemaQueries.upsert(
          collection = kind,
          scope_path = scope.toPath(),
          id = doc.id,
          payload = doc.payload,
          payload_schema = kind.schemaName,
          updated_at = doc.remoteTsMs,
          remote_updated_at = doc.remoteTsMs,
          dirty = false,
          deleted = doc.deleted,
        )
        if (doc.remoteTsMs > maxTs) maxTs = doc.remoteTsMs
      }
    }
    return maxTs
  }

  companion object {
    private const val TAG = "HydrationRunner"
  }
}
