package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.needsAttention
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Overview tab's task slice, read from [TaskStatusManager]. */
class TaskOverviewViewModel(
  taskStatusManager: TaskStatusManager,
  val thingId: ThingId,
) : ViewModel() {

  val uiState: StateFlow<TaskOverviewUiState> =
    taskStatusManager.observeTasksWithStatus(thingId)
      .distinctUntilChanged()
      .catch { emit(emptyList()) }
      .map { tasks ->
        val active = tasks.filter { it.dueStatus.status != DueStatus.COMPLIED }
        TaskOverviewUiState(
          loaded = true,
          overdueTasks = active.filter { it.needsAttention },
          nextDueTask = active.firstOrNull { !it.needsAttention },
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, TaskOverviewUiState())
}
