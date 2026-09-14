package dev.fanfly.wingslog.feature.export.datamanager.impl

import androidx.work.Data
import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.UUID

/** The request and progress must survive a round trip through WorkManager `Data` intact. */
class ExportWorkDataTest {

  private val request = ExportRequest(
    thingIds = listOf("thing-1", "thing-2"),
    dateRange = ExportDateRange.Custom(LocalDate(2026, 1, 1), LocalDate(2026, 6, 30)),
    includeOpenSquawks = false,
    formats = setOf(ExportFormat.CSV, ExportFormat.PDF),
    destinationEmail = "pilot@example.com",
    destinationEmailSource = "AUTH_FALLBACK",
  )
  private val success = ExportProgress.Success(
    exportId = "exp-1",
    filePath = "content://downloads/1",
    fileName = "x.zip",
    displayLocation = "",
    sizeBytes = 42L,
    displayLocationKind = ExportDisplayLocation.DOWNLOADS_SQUAWKIT,
    persistedDeliveryState = "NOT_REQUESTED",
    deliveryFailureMessage = "",
  )

  @Test
  fun `request round-trips through Data`() {
    assertThat(ExportWorkData.decodeRequest(ExportWorkData.encodeRequest(request)))
      .isEqualTo(request)
    val allTime = request.copy(dateRange = ExportDateRange.AllTime, destinationEmail = null)
    assertThat(ExportWorkData.decodeRequest(ExportWorkData.encodeRequest(allTime)))
      .isEqualTo(allTime)
    val months = request.copy(dateRange = ExportDateRange.LastNMonths(12))
    assertThat(ExportWorkData.decodeRequest(ExportWorkData.encodeRequest(months)))
      .isEqualTo(months)
  }

  @Test
  fun `progress round-trips through Data`() {
    val running = ExportProgress.Running(ExportProgressStep.UPLOADING_ARCHIVE, 86)
    assertThat(ExportWorkData.decodeProgress(ExportWorkData.encode(request, running)))
      .isEqualTo(running)
    assertThat(ExportWorkData.decodeProgress(ExportWorkData.encode(request, success)))
      .isEqualTo(success)
    val error = ExportProgress.Error("disk full")
    assertThat(ExportWorkData.decodeProgress(ExportWorkData.encode(request, error)))
      .isEqualTo(error)
    assertThat(ExportWorkData.decodeProgress(Data.EMPTY)).isNull()
  }

  @Test
  fun `a running WorkInfo with a progress echo rebuilds the job`() {
    val running = ExportProgress.Running(ExportProgressStep.SAVING_FILE, 74)
    val info = workInfo(
      state = WorkInfo.State.RUNNING,
      progress = ExportWorkData.encode(request, running),
    )

    val job = ExportWorkData.toJob(info, pendingRequest = null)!!

    assertThat(job.id).isEqualTo(info.id.toString())
    assertThat(job.request).isEqualTo(request)
    assertThat(job.progress).isEqualTo(running)
  }

  @Test
  fun `an enqueued WorkInfo without an echo falls back to the pending request`() {
    val info = workInfo(state = WorkInfo.State.ENQUEUED)

    assertThat(ExportWorkData.toJob(info, pendingRequest = null)).isNull()
    val job = ExportWorkData.toJob(info, pendingRequest = request)!!
    assertThat(job.request).isEqualTo(request)
    assertThat(job.progress)
      .isEqualTo(ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 0))
  }

  @Test
  fun `finished WorkInfo rebuilds the outcome from its output`() {
    val succeeded = workInfo(
      state = WorkInfo.State.SUCCEEDED,
      output = ExportWorkData.encode(request, success),
    )
    assertThat(ExportWorkData.toJob(succeeded, pendingRequest = null)?.progress)
      .isEqualTo(success)

    val failed = workInfo(
      state = WorkInfo.State.FAILED,
      output = ExportWorkData.encode(request, ExportProgress.Error("boom")),
    )
    assertThat(ExportWorkData.toJob(failed, pendingRequest = null)?.progress)
      .isEqualTo(ExportProgress.Error("boom"))
  }

  @Test
  fun `cancelled WorkInfo is no job`() {
    val info = workInfo(
      state = WorkInfo.State.CANCELLED,
      progress = ExportWorkData.encode(request, ExportProgress.Running(ExportProgressStep.SAVING_FILE, 74)),
    )
    assertThat(ExportWorkData.toJob(info, pendingRequest = request)).isNull()
  }

  private fun workInfo(
    state: WorkInfo.State,
    output: Data = Data.EMPTY,
    progress: Data = Data.EMPTY,
  ) = WorkInfo(UUID.randomUUID(), state, emptySet(), output, progress)
}
