package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The suggested tasks just added, held in memory for the task tab (1f): it says how many with an
 * *Undo* once. The suggestions screen records a batch; the task tab reads it.
 */
class RecentlyAddedTasks {
  private val _batch = MutableStateFlow<AddedBatch?>(null)
  val batch: StateFlow<AddedBatch?> = _batch.asStateFlow()

  fun record(thingId: String, taskIds: List<String>) {
    if (taskIds.isEmpty()) return
    _batch.value = AddedBatch(thingId, taskIds)
  }

  /** The tab has said how many were added; it does not say it again. */
  fun markAnnounced() {
    _batch.update { it?.copy(announced = true) }
  }

  /** *Undo*: the batch is gone. */
  fun clear() {
    _batch.value = null
  }
}

/** One acceptance's tasks on [thingId]; [announced] once the tab has shown its snackbar. */
data class AddedBatch(
  val thingId: String,
  val taskIds: List<String>,
  val announced: Boolean = false,
)
