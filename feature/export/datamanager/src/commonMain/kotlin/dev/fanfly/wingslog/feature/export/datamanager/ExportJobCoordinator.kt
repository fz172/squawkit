package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.coroutines.flow.StateFlow

/**
 * The one export this app is running or has just finished. [progress] is the latest
 * [ExportProgress]; a terminal value stays until [ExportJobCoordinator.clear] so a screen opened
 * after the fact still sees the outcome.
 */
data class ExportJob(
  val id: String,
  val request: ExportRequest,
  val progress: ExportProgress,
)

/**
 * Owns the lifetime of an export independently of any screen (#343). The export ViewModel only
 * submits, observes, and cancels; where the work actually runs is the platform's business — a
 * WorkManager job on Android, a plain coroutine elsewhere.
 *
 * At most one job exists at a time. [start] while one is running is a no-op.
 */
interface ExportJobCoordinator {
  val job: StateFlow<ExportJob?>

  fun start(request: ExportRequest)

  /** Stops a running job and forgets any finished one. */
  fun cancel()

  /** Forgets a finished job. A running one is left alone. */
  fun clear()
}

val ExportProgress.isTerminal: Boolean
  get() = this !is ExportProgress.Running
