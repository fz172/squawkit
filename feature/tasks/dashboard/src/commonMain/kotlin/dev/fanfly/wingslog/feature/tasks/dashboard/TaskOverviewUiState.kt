package dev.fanfly.wingslog.feature.tasks.dashboard

import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

/**
 * The Overview tab's task slice: overdue/due-soon tasks for the Needs Attention section, and the
 * next non-urgent active task for the wide-tier rail card.
 */
data class TaskOverviewUiState(
  val loaded: Boolean = false,
  val overdueTasks: List<MaintenanceTaskWithStatus> = emptyList(),
  val nextDueTask: MaintenanceTaskWithStatus? = null,
)
