package dev.fanfly.wingslog.core.storage

import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Deletes synced tombstone rows older than [retention]. Called once on app start.
 *
 * The `dirty=0` predicate is essential: a long-offline device must keep its un-pushed deletes
 * around so they reach Firestore on reconnect. Without that guard, GC would resurrect the docs.
 */
@OptIn(ExperimentalTime::class)
class TombstoneGc(
  private val db: WingsLogDatabase,
  private val retention: Duration = 30.days,
) {
  fun runOnce(now: Instant = Clock.System.now()) {
    val cutoffMs = (now - retention).toEpochMilliseconds()
    db.schemaQueries.gcTombstones(cutoffMs)
  }
}
