package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Narrows the squawk and task lists by their tab’s filter; the raw lists stay untouched. */
internal fun ThingOverviewUiState.Success.applyFilters(
  squawkFilter: RecordFilter,
  taskFilter: RecordFilter,
  searchEngine: SearchEngine,
  logs: List<MaintenanceLog>,
  today: LocalDate,
  timeZone: TimeZone,
): ThingOverviewUiState.Success {
  val logDates = logs.mapNotNull { log -> log.timestamp?.let { log.id to it.toLocalDate(timeZone) } }.toMap()
  val squawkAdapter = SquawkAdapter(timeZone, logDates)
  val taskAdapter = TaskAdapter()
  return copy(
    squawkFilter = squawkFilter,
    taskFilter = taskFilter,
    filteredSquawks = searchEngine.search(squawks, squawkAdapter, squawkFilter, today).map { it.item },
    filteredActiveTasks = searchEngine.search(activeTasks, taskAdapter, taskFilter, today).map { it.item },
    filteredCompletedTasks = searchEngine.search(completedTasks, taskAdapter, taskFilter, today).map { it.item },
  )
}
