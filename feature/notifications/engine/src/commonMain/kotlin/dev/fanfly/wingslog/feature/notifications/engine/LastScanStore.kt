package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.Instant

/**
 * What the last completed scan on this device did (design §11's diagnostics), and by extension when
 * it ran — which is what the session-boundary debounce reads (§6.6).
 *
 * One record rather than a timestamp plus a separate diagnostics blob: the debounce wants
 * [ScanRecord.at] and Developer Options wants the rest, and they are written at the same instant by
 * the same scan.
 *
 * @param recordsExamined tasks + squawks ranked across the whole fleet.
 * @param crossingsFound records whose rank rose above their watermark, before preferences.
 * @param crossingsSuppressed how many of those a switched-off tier dropped — the number that
 *   explains a scan which "found something" yet posted nothing.
 */
data class ScanRecord(
  val at: Instant,
  val trigger: ScanTrigger,
  val recordsExamined: Int,
  val crossingsFound: Int,
  val crossingsSuppressed: Int,
  val notificationsPosted: Int,
)

/**
 * Persists the last [ScanRecord] per user in `sync_config` — the same local, unsynced, uid-keyed
 * table `SyncPreferences` uses, and unsynced for the reason [UrgencyWatermarkStore] is: "what did
 * *this device* last do" is per-device state.
 *
 * **Persisted rather than held in memory**, even though only Developer Options reads the counts.
 * The scans worth debugging are the background ones, which by definition ran while the app was not
 * open; in-memory diagnostics would be empty for exactly the case §11 exists to explain.
 *
 * Encoded as a pipe-delimited versioned line rather than JSON — this is one internal row that never
 * leaves the device, and `engine` has no serialization dependency to add for it. Anything that does
 * not parse reads as `null`, i.e. "never scanned", which scans rather than staying silent.
 */
class LastScanStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  suspend fun lastScan(uid: String): ScanRecord? =
    db.schemaQueries.selectConfig(uid, KEY_LAST_SCAN)
      .awaitAsOneOrNull()
      ?.let(::decode)

  /** Just the instant, for the [ScanTrigger.SESSION_BOUNDARY] debounce. */
  suspend fun lastScanAt(uid: String): Instant? = lastScan(uid)?.at

  suspend fun record(uid: String, record: ScanRecord) {
    writeLock.withLock {
      db.schemaQueries.upsertConfig(uid, KEY_LAST_SCAN, encode(record))
    }
  }

  /** Developer Options' watermark reset also clears this, so the next scan reads as a first scan. */
  suspend fun clear(uid: String) {
    writeLock.withLock {
      db.schemaQueries.deleteConfig(uid, KEY_LAST_SCAN)
    }
  }

  private fun encode(r: ScanRecord): String = listOf(
    VERSION,
    r.at.toEpochMilliseconds(),
    r.trigger.name,
    r.recordsExamined,
    r.crossingsFound,
    r.crossingsSuppressed,
    r.notificationsPosted,
  ).joinToString("|")

  private fun decode(raw: String): ScanRecord? {
    val parts = raw.split('|')
    if (parts.size != 7 || parts[0] != VERSION) return null
    val at = parts[1].toLongOrNull() ?: return null
    val trigger =
      ScanTrigger.entries.firstOrNull { it.name == parts[2] } ?: return null
    val counts = parts.drop(3)
      .map { it.toIntOrNull() ?: return null }
    return ScanRecord(
      at = Instant.fromEpochMilliseconds(at),
      trigger = trigger,
      recordsExamined = counts[0],
      crossingsFound = counts[1],
      crossingsSuppressed = counts[2],
      notificationsPosted = counts[3],
    )
  }

  private companion object {
    const val KEY_LAST_SCAN = "urgency_last_scan"

    /** Bump when the field list changes; an older line then decodes to null and one scan re-seeds it. */
    const val VERSION = "1"
  }
}
