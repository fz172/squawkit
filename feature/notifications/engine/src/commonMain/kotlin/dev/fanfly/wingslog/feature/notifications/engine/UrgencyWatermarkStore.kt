package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.Clock

/**
 * One row per (uid, collection, scope, id): the urgency rank [UrgencyScanner] already reported for
 * that record, so the next scan only notifies on a further escalation. See design §6.2.
 *
 * **Deliberately not synced.** Entities sync; a synced watermark would let one device's scan
 * silence another's — the phone reports a crossing, the tablet sees the mark already at the
 * current rank and stays quiet forever. Per-device local state is the correct semantics here, the
 * same reason `sync_config` sits outside the sync engine.
 */
data class UrgencyWatermark(
  val uid: String,
  val collection: CollectionKind,
  val scope: EntityScope,
  val id: String,
  val rank: Int,
)

class UrgencyWatermarkStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  /** Every watermark this user has under [scopePrefix], for the scanner to build its lookup once rather than querying per aircraft. */
  suspend fun selectInScopePrefix(
    uid: String,
    scopePrefix: String,
  ): List<UrgencyWatermark> =
    db.schemaQueries.selectWatermarksInScopePrefix(uid, scopePrefix)
      .awaitAsList()
      .map {
        UrgencyWatermark(
          uid = it.uid,
          collection = it.collection,
          scope = EntityScope(
            it.scope_path.trim('/')
              .split('/')
          ),
          id = it.id,
          rank = it.rank.toInt(),
        )
      }

  /** Moves the watermark up or down to [rank] — always, even on a de-escalation (design §6.3): a task that is complied and later comes due again must notify again. */
  suspend fun upsert(
    uid: String,
    collection: CollectionKind,
    scope: EntityScope,
    id: String,
    rank: Int,
  ) {
    writeLock.withLock {
      db.schemaQueries.upsertWatermark(
        uid = uid,
        collection = collection,
        scope_path = scope.toPath(),
        id = id,
        rank = rank.toLong(),
        updated_at = Clock.System.now()
          .toEpochMilliseconds(),
      )
    }
  }

  /**
   * Drops watermark rows under [scope] for [collection] whose id is not in [seenIds] — a deleted
   * record would otherwise leave a watermark row forever (design §6.4). Call once per (collection,
   * scope) the scan actually visited; never broader, or an un-hydrated aircraft's history is erased.
   */
  suspend fun pruneNotIn(
    uid: String,
    collection: CollectionKind,
    scope: EntityScope,
    seenIds: Collection<String>,
  ) {
    writeLock.withLock {
      db.schemaQueries.deleteWatermarksNotIn(
        uid = uid,
        collection = collection,
        scopePath = scope.toPath(),
        ids = seenIds,
      )
    }
  }

  /**
   * Account deletion, and — as of 2026-08-22 — sign-out, called via
   * `DatabaseIntegrityChecker.wipeDataForUser`, which is where the actual production caller lives
   * (this class stays in `:engine`, which `core:storage` cannot depend on, so that path calls the
   * same underlying query directly rather than through this store). The integrity-check wipe alone
   * still leaves these rows in place, for the reason `sync_config` is excluded from
   * `wipeAllEntities` — that event never hands the device to a different account (design §6.2).
   */
  suspend fun deleteForUser(uid: String) {
    writeLock.withLock {
      db.schemaQueries.deleteWatermarksForUser(uid)
    }
  }
}
