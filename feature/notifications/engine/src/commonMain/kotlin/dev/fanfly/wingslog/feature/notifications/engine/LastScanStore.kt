package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.Instant

/**
 * When this device last completed an urgency scan, per user, so the session-boundary trigger can
 * debounce (design §6.6).
 *
 * Kept in `sync_config` — the same local, unsynced, uid-keyed table `SyncPreferences` uses, and for
 * the same reason [UrgencyWatermarkStore] is unsynced: "when did *this device* last scan" is
 * per-device state. Syncing it would let a scan on the phone suppress the tablet's first scan of
 * the day.
 *
 * Stored as epoch milliseconds in the table's `TEXT` value column. An unparseable or missing value
 * reads as `null`, which the caller treats as "never scanned" — the safe direction, since it scans
 * rather than staying silent.
 */
class LastScanStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  suspend fun lastScanAt(uid: String): Instant? =
    db.schemaQueries.selectConfig(uid, KEY_LAST_SCAN_AT)
      .awaitAsOneOrNull()
      ?.toLongOrNull()
      ?.let { Instant.fromEpochMilliseconds(it) }

  suspend fun record(uid: String, at: Instant) {
    writeLock.withLock {
      db.schemaQueries.upsertConfig(
        uid,
        KEY_LAST_SCAN_AT,
        at.toEpochMilliseconds()
          .toString(),
      )
    }
  }

  private companion object {
    const val KEY_LAST_SCAN_AT = "urgency_last_scan_at"
  }
}
