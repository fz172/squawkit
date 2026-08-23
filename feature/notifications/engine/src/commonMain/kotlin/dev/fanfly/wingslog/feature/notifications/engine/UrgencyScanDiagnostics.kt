package dev.fanfly.wingslog.feature.notifications.engine

import dev.gitlive.firebase.auth.FirebaseAuth

/**
 * The two Developer Options actions that need the signed-in uid (design §11), behind one injectable
 * thing so `devoptions` does not have to take a Firebase dependency to read a diagnostic.
 */
class UrgencyScanDiagnostics(
  private val auth: FirebaseAuth,
  private val watermarkStore: UrgencyWatermarkStore,
  private val lastScanStore: LastScanStore,
) {

  /** What the last completed scan on this device did, or `null` if it has never completed one. */
  suspend fun lastScan(): ScanRecord? {
    val uid = auth.currentUser?.uid ?: return null
    return lastScanStore.lastScan(uid)
  }

  /**
   * Drops every watermark for the signed-in user, re-arming every crossing so the seeding rules
   * (§6.4) can be exercised again on one device. Returns false when signed out.
   *
   * Clears the last-scan record too. Leaving it would debounce the very next session-boundary scan
   * — the one the reset exists to make interesting — and would report counts describing a scan
   * whose watermarks no longer exist.
   *
   * Note the whole fleet re-seeds *silently* on the next scan: with no rows under a scope,
   * `aircraftKnown` is false and §6.4 seeds every record without reporting. Two scans are needed to
   * see a notification — one to seed, then a change, then another.
   */
  suspend fun resetWatermarks(): Boolean {
    val uid = auth.currentUser?.uid ?: return false
    watermarkStore.deleteForUser(uid)
    lastScanStore.clear(uid)
    return true
  }
}
