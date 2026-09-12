package dev.fanfly.wingslog.feature.export.datamanager.impl

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportJob
import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.export.datamanager.isTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android [ExportJobCoordinator]: one unique `ExportWorker` at a time, observed through
 * WorkManager's own `WorkInfo` flow so the job state has no store of its own and survives process
 * death for free. `KEEP` makes a second [start] while one runs a no-op, matching the contract.
 */
class WorkManagerExportJobCoordinator(
  private val context: Context,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ExportJobCoordinator, KoinComponent {

  private val notifications: ExportNotifications by inject()
  private val wm get() = WorkManager.getInstance(context)

  // Bridges the gap between enqueue and the worker's first progress echo, when WorkInfo carries no
  // request yet. Not persisted: after process death in that window the job simply shows up once
  // the worker starts.
  private var pendingRequest: ExportRequest? = null

  override val job: StateFlow<ExportJob?> =
    wm.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
      .map { infos -> infos.firstOrNull()?.let { ExportWorkData.toJob(it, pendingRequest) } }
      .stateIn(scope, SharingStarted.Eagerly, null)

  override fun start(request: ExportRequest) {
    if (job.value?.progress?.isTerminal == false) return
    pendingRequest = request
    notifications.cancelResult()
    wm.enqueueUniqueWork(
      UNIQUE_NAME,
      ExistingWorkPolicy.KEEP,
      OneTimeWorkRequestBuilder<ExportWorker>()
        .setInputData(ExportWorkData.encodeRequest(request))
        .build(),
    )
  }

  override fun cancel() {
    pendingRequest = null
    notifications.cancelResult()
    wm.cancelUniqueWork(UNIQUE_NAME)
    // A finished job is not cancellable; forgetting it means pruning its WorkInfo.
    if (job.value?.progress?.isTerminal == true) wm.pruneWork()
  }

  override fun clear() {
    if (job.value?.progress?.isTerminal != true) return
    pendingRequest = null
    notifications.cancelResult()
    wm.pruneWork()
  }

  private companion object {
    const val UNIQUE_NAME = "export"
  }
}
