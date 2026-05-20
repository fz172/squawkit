package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import com.squareup.wire.Instant as WireInstant

/**
 * Builds a consistent, in-memory export snapshot for one aircraft.
 */
class LogbookExportAggregator(
  private val fleetManager: FleetManager,
  private val logsManager: MaintenanceLogManager,
  private val tasksManager: TaskDataManager,
  private val taskDueManager: TaskDueManager,
  private val squawkManager: SquawkManager,
  private val technicianManager: TechnicianManager,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
  /**
   * Collects aircraft, logs, tasks, squawks, and technician data for [aircraftId].
   *
   * The returned bundle is date-filtered for timestamped records and sorted oldest first for
   * paper-logbook order.
   */
  suspend fun collect(request: ExportRequest, aircraftId: String): AircraftBundle = coroutineScope {
    val aircraftDeferred = async { fleetManager.loadAircraft(aircraftId).first() }
    val logsDeferred = async { logsManager.observeLogs(aircraftId).first() }
    val tasksDeferred = async { tasksManager.observeTasks(aircraftId).first() }
    val squawksDeferred = async { squawkManager.observeSquawks(aircraftId).first() }

    val aircraft = requireNotNull(aircraftDeferred.await())
    val allLogs = logsDeferred.await()
    val allTasks = tasksDeferred.await()
    val allSquawks = squawksDeferred.await()

    val logsInRange = allLogs
      .filter { log -> request.dateRange.includes(log.timestamp) }
      .sortedBy { it.timestamp?.getEpochSecond() ?: Long.MIN_VALUE }
    val squawksInRange = allSquawks
      .filter { squawk -> request.dateRange.includes(squawk.created_at) }
      .let { squawks ->
        if (request.includeOpenSquawks) squawks else squawks.filter { it.isClosed() }
      }
      .sortedBy { it.created_at?.getEpochSecond() ?: Long.MIN_VALUE }
    // Tasks are a recurring program, not dated events, so a date range scopes them to the work
    // actually recorded in that window: keep a task only if an in-range log complied with it.
    // "All time" keeps the full program.
    val tasksInRange = if (request.dateRange is ExportDateRange.AllTime) {
      allTasks
    } else {
      val workedOnTaskIds = logsInRange.flatMap { it.inspection_ids }.toSet()
      allTasks.filter { it.id in workedOnTaskIds }
    }
    val techniciansById = resolveTechnicians(logsInRange)
    // Next-due is a forward-looking fact, so it is computed from the full history; last-complied
    // reflects the in-range compliance shown alongside the task.
    val dueByTaskId = tasksInRange.associate { task ->
      task.id to taskDueManager.computeNextDue(task, allLogs, allTasks)
    }
    val lastCompliedByTaskId = tasksInRange.associate { task ->
      task.id to logsInRange
        .filter { log -> task.id in log.inspection_ids }
        .maxByOrNull { log -> log.timestamp?.getEpochSecond() ?: Long.MIN_VALUE }
    }.filterValues { it != null }.mapValues { (_, log) -> log!! }

    AircraftBundle(
      aircraft = aircraft,
      logs = logsInRange,
      tasks = tasksInRange,
      dueByTaskId = dueByTaskId,
      lastCompliedByTaskId = lastCompliedByTaskId,
      squawks = squawksInRange,
      // Full map so log rows can still resolve inspection titles, references, and linked schedules
      // for tasks that fall outside the displayed list.
      tasksById = allTasks.associateBy { it.id },
      squawksById = allSquawks.associateBy { it.id },
      techniciansById = techniciansById,
    )
  }

  private fun ExportDateRange.includes(timestamp: WireInstant?): Boolean {
    if (this is ExportDateRange.AllTime) return true
    val date = timestamp?.toLocalDate(timeZone) ?: return false
    return date in bounds()
  }

  private fun ExportDateRange.bounds(): ClosedRange<LocalDate> {
    val today = clock.now().toLocalDateTime(timeZone).date
    return when (this) {
      ExportDateRange.AllTime -> LocalDate(1, 1, 1)..LocalDate(9999, 12, 31)
      is ExportDateRange.LastNMonths -> today.minus(DatePeriod(months = months))..today
      is ExportDateRange.Custom -> start..endInclusive
    }
  }

  private suspend fun resolveTechnicians(logs: List<MaintenanceLog>): Map<String, Technician> {
    val embedded = logs.mapNotNull { log ->
      log.technician
        ?.takeIf { technician -> technician.id.isNotBlank() && technician.name.isNotBlank() }
        ?.let { technician -> technician.id to technician }
    }.toMap()
    val missingIds = logs.mapNotNull { log ->
      log.technician_id.takeIf { id -> id.isNotBlank() }
    }.toSet() - embedded.keys
    val resolved = missingIds.mapNotNull { id ->
      technicianManager.loadTechnician(id).first()?.let { technician -> id to technician }
    }.toMap()
    return embedded + resolved
  }

  private fun Squawk.isClosed(): Boolean =
    addressed_by_log_id.isNotBlank() ||
      dismiss_reason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN
}
