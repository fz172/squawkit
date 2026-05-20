package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import com.squareup.wire.Instant as WireInstant

class LogbookExportAggregatorTest {

  private val aircraftId = "ac-1"
  private val jan2020 = WireInstant.ofEpochSecond(1_577_836_800L) // 2020-01-01T00:00:00Z
  private val jan2025 = WireInstant.ofEpochSecond(1_735_689_600L) // 2025-01-01T00:00:00Z

  private val log2020 = MaintenanceLog(
    id = "log-2020",
    work_description = "2020 annual",
    component_type = ComponentType.COMPONENT_AIRFRAME,
    timestamp = jan2020,
    inspection_ids = listOf("task-old"),
  )
  private val log2025 = MaintenanceLog(
    id = "log-2025",
    work_description = "2025 annual",
    component_type = ComponentType.COMPONENT_AIRFRAME,
    timestamp = jan2025,
    inspection_ids = listOf("task-new"),
  )
  private val taskOld = MaintenanceTask(id = "task-old", title = "Old check")
  private val taskNew = MaintenanceTask(id = "task-new", title = "New check")

  private fun aggregator(): LogbookExportAggregator {
    val fleetManager = mockk<FleetManager> {
      every { loadAircraft(aircraftId) } returns flowOf(
        Aircraft(id = aircraftId, make = "Cessna", model = "172", serial = "1", tail_number = "N12345"),
      )
    }
    val logsManager = mockk<MaintenanceLogManager> {
      every { observeLogs(aircraftId) } returns flowOf(listOf(log2020, log2025))
    }
    val tasksManager = mockk<TaskDataManager> {
      every { observeTasks(aircraftId) } returns flowOf(listOf(taskOld, taskNew))
    }
    val squawkManager = mockk<SquawkManager> {
      every { observeSquawks(aircraftId) } returns flowOf(emptyList<Squawk>())
    }
    return LogbookExportAggregator(
      fleetManager = fleetManager,
      logsManager = logsManager,
      tasksManager = tasksManager,
      taskDueManager = mockk<TaskDueManager>(relaxed = true),
      squawkManager = squawkManager,
      technicianManager = mockk<TechnicianManager>(relaxed = true),
      timeZone = TimeZone.UTC,
    )
  }

  @Test
  fun collect_customRange_keepsOnlyLogsAndTasksWithinRange() = runTest {
    val bundle = aggregator().collect(
      request = ExportRequest(
        aircraftIds = listOf(aircraftId),
        dateRange = ExportDateRange.Custom(LocalDate(2020, 1, 1), LocalDate(2020, 12, 31)),
        includeOpenSquawks = true,
      ),
      aircraftId = aircraftId,
    )

    assertThat(bundle.logs.map { it.id }).containsExactly("log-2020")
    // A 2020 export must not surface the task whose only compliance is the 2025 log.
    assertThat(bundle.tasks.map { it.id }).containsExactly("task-old")
  }

  @Test
  fun collect_allTime_keepsEveryLogAndTask() = runTest {
    val bundle = aggregator().collect(
      request = ExportRequest(
        aircraftIds = listOf(aircraftId),
        dateRange = ExportDateRange.AllTime,
        includeOpenSquawks = true,
      ),
      aircraftId = aircraftId,
    )

    assertThat(bundle.logs.map { it.id }).containsExactly("log-2020", "log-2025")
    assertThat(bundle.tasks.map { it.id }).containsExactly("task-old", "task-new")
  }
}
