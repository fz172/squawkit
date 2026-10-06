package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * The suggested tasks just added to this Thing (1f): how many, to say once with *Undo*.
 */
class AddedTasksViewModel(
  private val recentlyAdded: RecentlyAddedTasks,
  private val taskDataManager: TaskDataManager,
  private val thingId: String,
) : ViewModel() {

  /**
   * The ids of each batch added to this Thing, once: a batch is taken as it is handed out, so
   * showing its snackbar changes nothing the snackbar depends on.
   */
  val added: Flow<List<String>> =
    recentlyAdded.batch.mapNotNull { recentlyAdded.take(thingId)?.taskIds }

  /** *Undo* on a batch's snackbar: its tasks are deleted. */
  fun onUndo(taskIds: List<String>) {
    viewModelScope.launch { taskIds.forEach { taskDataManager.deleteTask(thingId, it) } }
  }
}
