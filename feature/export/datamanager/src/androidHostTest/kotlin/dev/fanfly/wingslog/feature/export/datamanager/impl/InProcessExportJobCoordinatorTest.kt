package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The coordinator runs on the test scope itself: `advanceUntilIdle` does not drive `backgroundScope` on its own. */
@OptIn(ExperimentalCoroutinesApi::class)
class InProcessExportJobCoordinatorTest {

  private val request = ExportRequest(
    thingIds = listOf("thing-1"),
    dateRange = ExportDateRange.AllTime,
    includeOpenSquawks = true,
  )
  private val success = ExportProgress.Success(
    exportId = "exp-1",
    filePath = "/tmp/x.zip",
    fileName = "x.zip",
    displayLocation = "",
    sizeBytes = 1L,
    displayLocationKind = ExportDisplayLocation.FILES_SQUAWKIT,
  )
  private val exportManager: ExportManager = mockk()

  @Test
  fun `start runs the export and keeps the terminal outcome until cleared`() = runTest {
    every { exportManager.exportLogs(request) } returns flow {
      emit(ExportProgress.Running(ExportProgressStep.COMPRESSING_ARCHIVE, 60))
      emit(success)
    }
    val coordinator = InProcessExportJobCoordinator(exportManager, this)

    coordinator.start(request)
    assertThat(coordinator.job.value?.progress)
      .isEqualTo(ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 0))
    advanceUntilIdle()
    assertThat(coordinator.job.value?.progress).isEqualTo(success)
    assertThat(coordinator.job.value?.request).isEqualTo(request)

    // A second start while a finished job is showing replaces it; while running it would not.
    coordinator.clear()
    assertThat(coordinator.job.value).isNull()
  }

  @Test
  fun `cancel drops a running job`() = runTest {
    every { exportManager.exportLogs(request) } returns flow {
      emit(ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 8))
      awaitCancellation()
    }
    val coordinator = InProcessExportJobCoordinator(exportManager, this)

    coordinator.start(request)
    advanceUntilIdle()
    assertThat(coordinator.job.value?.progress)
      .isEqualTo(ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 8))

    coordinator.cancel()
    advanceUntilIdle()
    assertThat(coordinator.job.value).isNull()
  }

  @Test
  fun `a throwing pipeline becomes an Error outcome, not a crash`() = runTest {
    every { exportManager.exportLogs(request) } returns flow {
      emit(ExportProgress.Running(ExportProgressStep.SAVING_FILE, 74))
      throw IllegalStateException("disk full")
    }
    val coordinator = InProcessExportJobCoordinator(exportManager, this)

    coordinator.start(request)
    advanceUntilIdle()

    val error = coordinator.job.value?.progress as ExportProgress.Error
    assertThat(error.message).isEqualTo("disk full")
    coordinator.clear()
    assertThat(coordinator.job.value).isNull()
  }

  @Test
  fun `start while running is ignored`() = runTest {
    every { exportManager.exportLogs(any()) } returns flow { awaitCancellation() }
    val coordinator = InProcessExportJobCoordinator(exportManager, this)

    coordinator.start(request)
    advanceUntilIdle()
    val first = coordinator.job.value
    coordinator.start(request.copy(thingIds = listOf("thing-2")))
    advanceUntilIdle()

    assertThat(coordinator.job.value).isEqualTo(first)
    coordinator.cancel()
  }
}
