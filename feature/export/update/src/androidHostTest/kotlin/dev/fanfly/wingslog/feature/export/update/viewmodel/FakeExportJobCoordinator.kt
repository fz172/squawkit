package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.feature.export.datamanager.ExportJob
import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.export.datamanager.isTerminal
import kotlinx.coroutines.flow.MutableStateFlow

/** Records calls and lets a test drive the job's progress by hand. */
class FakeExportJobCoordinator : ExportJobCoordinator {
  override val job = MutableStateFlow<ExportJob?>(null)
  var starts = 0
  var cancels = 0
  var clears = 0

  override fun start(request: ExportRequest) {
    starts++
    job.value = ExportJob(
      id = "job-$starts",
      request = request,
      progress = ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 0),
    )
  }

  override fun cancel() {
    cancels++
    job.value = null
  }

  override fun clear() {
    clears++
    if (job.value?.progress?.isTerminal == true) job.value = null
  }

  fun emit(progress: ExportProgress) {
    job.value = job.value?.copy(progress = progress)
  }
}
