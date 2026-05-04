package dev.fanfly.wingslog.feature.sync.data

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.Sync_cursor
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.Clock

/**
 * One row per (uid, kind, scope) describing how far the sync engine has progressed.
 *
 * - [hydrated] true once the initial bulk pull for this scope finished successfully.
 * - [lastSeenRemote] max Firestore `update_time` (in epoch ms) seen so far. The pull listener
 *   uses this as the lower bound on its `where("update_time", ">", cursor)` query.
 * - [failedAttempts] consecutive hydration failures; reset to 0 on success. Drives backoff.
 *
 * The cursor table keys on `uid` so a device can host multiple users without leaking progress
 * between them.
 */
data class SyncCursor(
  val uid: String,
  val kind: CollectionKind,
  val scope: EntityScope,
  val hydrated: Boolean,
  val lastSeenRemote: Long?,
  val failedAttempts: Int,
  val lastAttemptAt: Long?,
)

class SyncCursorStore(private val db: WingsLogDatabase) {

  fun get(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
  ): SyncCursor? =
    db.schemaQueries.selectCursor(
      uid,
      kind,
      scope.toPath()
    ).executeAsOneOrNull()?.toCursor(scope)

  fun markHydrated(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
    lastSeenRemote: Long?,
  ) {
    db.schemaQueries.upsertCursor(
      uid = uid,
      collection = kind,
      scope_path = scope.toPath(),
      hydrated = true,
      last_seen_remote = lastSeenRemote,
      failed_attempts = 0L,
      last_attempt_at = Clock.System.now().toEpochMilliseconds(),
    )
  }

  fun advanceLastSeen(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
    remoteTs: Long,
  ) {
    val current = get(
      uid,
      kind,
      scope
    )
    if (current != null && current.lastSeenRemote != null && current.lastSeenRemote >= remoteTs) {
      return
    }
    db.schemaQueries.upsertCursor(
      uid = uid,
      collection = kind,
      scope_path = scope.toPath(),
      hydrated = current?.hydrated ?: false,
      last_seen_remote = remoteTs,
      failed_attempts = current?.failedAttempts?.toLong() ?: 0L,
      last_attempt_at = current?.lastAttemptAt ?: Clock.System.now().toEpochMilliseconds(),
    )
  }

  fun recordFailure(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
  ) {
    val current = get(
      uid,
      kind,
      scope
    )
    db.schemaQueries.upsertCursor(
      uid = uid,
      collection = kind,
      scope_path = scope.toPath(),
      hydrated = current?.hydrated ?: false,
      last_seen_remote = current?.lastSeenRemote,
      failed_attempts = ((current?.failedAttempts ?: 0) + 1).toLong(),
      last_attempt_at = Clock.System.now().toEpochMilliseconds(),
    )
  }

  private fun Sync_cursor.toCursor(scope: EntityScope): SyncCursor = SyncCursor(
    uid = uid,
    kind = collection,
    scope = scope,
    hydrated = hydrated,
    lastSeenRemote = last_seen_remote,
    failedAttempts = failed_attempts.toInt(),
    lastAttemptAt = last_attempt_at,
  )
}
