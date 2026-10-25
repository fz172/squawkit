package dev.fanfly.wingslog.feature.tasks.dashboard

import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

data class TaskTabUiState(
  val filter: RecordFilter = RecordFilter(),
  /** All active tasks before search/filter, used for filter chip counts and jump targets. */
  val allActiveTasks: List<MaintenanceTaskWithStatus> = emptyList(),
  /** All completed tasks before search/filter, used for filter chip counts and jump targets. */
  val allCompletedTasks: List<MaintenanceTaskWithStatus> = emptyList(),
  val activeTasks: List<MaintenanceTaskWithStatus> = emptyList(),
  val completedTasks: List<MaintenanceTaskWithStatus> = emptyList(),
  /** Task id → the words the query matched, for highlighting. */
  val matches: Map<String, List<FieldMatch>> = emptyMap(),
)
