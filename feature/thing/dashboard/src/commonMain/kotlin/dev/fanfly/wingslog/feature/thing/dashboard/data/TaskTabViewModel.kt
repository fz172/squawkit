package dev.fanfly.wingslog.feature.thing.dashboard.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TaskTabUiState(
  val filter: RecordFilter = RecordFilter(),
  val activeTasks: List<MaintenanceTaskWithStatus> = emptyList(),
  val completedTasks: List<MaintenanceTaskWithStatus> = emptyList(),
)

/** The Tasks tab’s own list and filter, read from the task feature’s due-status flow. */
class TaskTabViewModel(
  taskStatusManager: TaskStatusManager,
  private val searchEngine: SearchEngine,
  thingId: String,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

  private val _filter = MutableStateFlow(RecordFilter())
  private val adapter = TaskAdapter()

  val uiState: StateFlow<TaskTabUiState> = combine(
    taskStatusManager.observeTasksWithStatus(thingId).catch { emit(emptyList()) },
    _filter,
  ) { tasks, filter ->
    val today = clock.now().toLocalDateTime(timeZone).date
    val (complied, active) = tasks.partition { it.dueStatus.status == DueStatus.COMPLIED }
    TaskTabUiState(
      filter = filter,
      activeTasks = searchEngine.search(active, adapter, filter, today).map { it.item },
      completedTasks = searchEngine.search(complied, adapter, filter, today).map { it.item },
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, TaskTabUiState())

  fun onFilterChange(filter: RecordFilter) {
    _filter.value = filter
  }

  fun clearFilter() {
    _filter.value = RecordFilter()
  }
}
