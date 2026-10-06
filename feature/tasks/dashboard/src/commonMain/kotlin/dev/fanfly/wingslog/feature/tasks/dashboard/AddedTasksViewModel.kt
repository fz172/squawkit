package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The suggested tasks just added to this Thing (1f): how many, to say once with *Undo*, and which,
 * to mark NEW in the list.
 */
class AddedTasksViewModel(
  private val recentlyAdded: RecentlyAddedTasks,
  private val taskDataManager: TaskDataManager,
  private val thingId: String,
) : ViewModel() {

  private val batch = recentlyAdded.batch.map { it?.takeIf { batch -> batch.thingId == thingId } }

  /** The tasks to mark NEW. */
  val newIds: StateFlow<Set<String>> = batch
    .map { it?.taskIds.orEmpty().toSet() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

  /** How many were just added, until the tab has said so; null otherwise. */
  val toAnnounce: StateFlow<Int?> = batch
    .map { it?.takeIf { batch -> !batch.announced }?.taskIds?.size }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** The snackbar is up; it is not shown again. */
  fun onAnnounced() = recentlyAdded.markAnnounced()

  /** *Undo*: the batch's tasks are deleted, and nothing is marked NEW. */
  fun onUndo() {
    val ids = recentlyAdded.batch.value?.takeIf { it.thingId == thingId }?.taskIds ?: return
    recentlyAdded.clear()
    viewModelScope.launch { ids.forEach { taskDataManager.deleteTask(thingId, it) } }
  }
}
