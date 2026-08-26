package dev.fanfly.wingslog.feature.notifications.engine

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Android [UrgencyScanScheduler] backed by WorkManager (design §5.4). `WorkManagerUploadScheduler`
 * is the shape this copies, one unique periodic request instead of one-shots per blob.
 *
 * **No network constraint** — the scan reads SQLDelight and posts a local notification; nothing it
 * touches leaves the device. No time-of-day plumbing either: `PeriodicWorkRequest` has no
 * time-of-day API, and since the 2026-08-22 cadence change there is no 08:00 local to chase
 * (design §16 E1). The device's own clock is what `TaskDueManagerImpl` already uses.
 *
 * `KEEP` means a relaunch leaves an already-enqueued chain running rather than resetting its
 * period, so an app opened several times an hour still scans on the same 2h rhythm.
 *
 * OEM battery managers can still kill periodic work outright. That is a known and accepted gap —
 * the session-boundary scan (P2.8) is the backstop, not a second scheduler.
 */
class WorkManagerUrgencyScanScheduler(
  private val context: Context,
) : UrgencyScanScheduler {

  private val wm get() = WorkManager.getInstance(context)

  override fun ensureScheduled() {
    val request = PeriodicWorkRequestBuilder<UrgencyScanWorker>(
      repeatInterval = SCAN_INTERVAL.toJavaDuration(),
      flexTimeInterval = SCAN_FLEX.toJavaDuration(),
    ).addTag(TAG_URGENCY_SCAN).build()
    wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
  }

  override fun cancel() {
    wm.cancelUniqueWork(WORK_NAME)
  }

  companion object {
    const val WORK_NAME = "wingslog_urgency_scan"
    const val TAG_URGENCY_SCAN = "wingslog_urgency_scan"

    /** Design §6.6's target cadence. */
    private val SCAN_INTERVAL = 2.hours

    /** WorkManager's minimum flex; anything smaller is silently clamped to it. */
    private val SCAN_FLEX = 15.minutes
  }
}
