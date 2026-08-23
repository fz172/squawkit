package dev.fanfly.wingslog.feature.notifications.engine

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * iOS [UrgencyScanScheduler] backed by `BGAppRefreshTask` (design §5.4). `UrlSessionUploadScheduler`
 * registers a `BGProcessingTask` the same way; app-refresh is the right class here because the scan
 * is short, local, and wants no power or network constraint.
 *
 * **This is opportunistic, and the design says so.** `BGAppRefreshTask` is entirely iOS-scheduled —
 * it weighs engagement, battery, Low Power Mode, and a per-app budget, and [SCAN_INTERVAL_SECONDS]
 * is a lower bound the system is free to blow past by hours. A handful of runs a day is a normal
 * outcome for an app opened daily; a quiet day is normal for one that isn't. The session-boundary
 * scan (P2.8) is what actually delivers something near a 2h cadence on iOS. If the field metric
 * (design §6.6) says this never fires, the fallback is not a server — it is scanning on foreground
 * only, and saying so in the copy.
 *
 * iOS does not repeat a submission, so [handleScanTask] re-submits before completing the task.
 */
@OptIn(ExperimentalForeignApi::class)
class BgTaskUrgencyScanScheduler(
  private val scanner: UrgencyScanner,
) : UrgencyScanScheduler {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /**
   * Registers the launch handler with the OS. Must run before
   * `application:didFinishLaunchingWithOptions:` returns, and [BG_SCAN_TASK_ID] must appear in
   * `Info.plist`'s `BGTaskSchedulerPermittedIdentifiers` — hence the host call rather than a
   * `createdAtStart` single like Android's.
   */
  fun registerBgTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
      identifier = BG_SCAN_TASK_ID,
      usingQueue = null,
      launchHandler = { task ->
        if (task != null) handleScanTask(task as BGAppRefreshTask)
      },
    )
    log.d { "registered BGAppRefreshTask identifier $BG_SCAN_TASK_ID" }
  }

  override fun ensureScheduled() {
    val request = BGAppRefreshTaskRequest(identifier = BG_SCAN_TASK_ID).apply {
      earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(SCAN_INTERVAL_SECONDS)
    }
    try {
      BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    } catch (e: Exception) {
      // Simulator and an unregistered identifier both land here; neither is worth crashing over.
      log.w(e) { "failed to submit BGAppRefreshTask" }
    }
  }

  override fun cancel() {
    BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(BG_SCAN_TASK_ID)
  }

  private fun handleScanTask(task: BGAppRefreshTask) {
    // iOS gives an app-refresh task ~30s. Expiring mid-scan is safe to abandon: the scan commits
    // watermarks only after posting (design §6.7), so a killed run re-reports rather than drops.
    task.expirationHandler = { task.setTaskCompletedWithSuccess(false) }
    scope.launch {
      val result = runCatching { scanner.scan(ScanTrigger.SCHEDULED) }
      result.exceptionOrNull()
        ?.let { log.w(it) { "background urgency scan failed" } }
        ?: log.d { "background urgency scan: ${result.getOrNull()}" }
      // A submission is single-shot — without this the app scans in the background exactly once.
      ensureScheduled()
      task.setTaskCompletedWithSuccess(result.isSuccess)
    }
  }

  companion object {
    /** Must match `Info.plist`'s `BGTaskSchedulerPermittedIdentifiers`. */
    const val BG_SCAN_TASK_ID = "dev.fanfly.wingslog.urgency-scan"

    /** Design §6.6's 2h target, as the lower bound iOS treats it as. */
    private const val SCAN_INTERVAL_SECONDS = 2.0 * 60 * 60

    private val log = Logger.withTag("BgTaskUrgencyScanScheduler")
  }
}
