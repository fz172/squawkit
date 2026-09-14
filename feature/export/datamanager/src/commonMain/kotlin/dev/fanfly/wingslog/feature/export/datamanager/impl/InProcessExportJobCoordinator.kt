package dev.fanfly.wingslog.feature.export.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.feature.export.datamanager.ExportJob
import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.export.datamanager.isTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * Runs the export as a coroutine in this process (iOS, web). Nothing keeps it alive past the
 * process, and the export ViewModel cancels it when the screen goes away unless
 * `ExportRunPolicy.survivesLeavingScreen` says otherwise.
 */
class InProcessExportJobCoordinator(
  private val exportManager: ExportManager,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ExportJobCoordinator {

  private val _job = MutableStateFlow<ExportJob?>(null)
  override val job: StateFlow<ExportJob?> = _job.asStateFlow()

  private var running: Job? = null

  override fun start(request: ExportRequest) {
    if (_job.value?.progress?.isTerminal == false) return
    val id = generateRandomId()
    _job.value = ExportJob(id, request, ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 0))
    running = scope.launch {
      exportManager.exportLogs(request)
        // A failure anywhere in the pipeline is an Error outcome, not an unhandled exception.
        .catch { t ->
          log.e(t) { "export failed" }
          emit(ExportProgress.Error(t.message.orEmpty(), t))
        }
        .onCompletion { cause ->
          // Cancelled before a terminal value: the job is gone, not finished.
          if (cause != null && _job.value?.id == id) _job.value = null
        }
        .collect { progress ->
          if (_job.value?.id == id) _job.value = ExportJob(id, request, progress)
        }
    }
  }

  override fun cancel() {
    running?.cancel()
    running = null
    _job.value = null
  }

  override fun clear() {
    if (_job.value?.progress?.isTerminal == true) _job.value = null
  }

  private companion object {
    val log = Logger.withTag("InProcessExportJobCoordinator")
  }
}
