package dev.fanfly.wingslog.feature.tasks.dashboard.detail

import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.thing.MaintenanceLog

data class TaskDetailUiState(
  val selectedTask: MaintenanceTaskWithStatus? = null,
  val logsForSelectedTask: List<MaintenanceLog> = emptyList(),
  /** Resolve bubble open for this task (design §5.1). */
  val resolvingTaskId: MaintenanceTaskId? = null,
  /** Skip-this-cycle confirmation open for this task. */
  val skippingTaskId: MaintenanceTaskId? = null,
  /** Delete confirmation open for this task. */
  val deletingTaskId: MaintenanceTaskId? = null,
  val deletingTaskTitle: String = "",
)
