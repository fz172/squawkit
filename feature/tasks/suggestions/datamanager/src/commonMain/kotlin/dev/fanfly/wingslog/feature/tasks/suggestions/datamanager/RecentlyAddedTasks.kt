package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * The suggested tasks just added, held in memory for the task tab (1f): it says how many with an
 * *Undo* once. The suggestions screen records a batch; the task tab takes it.
 */
class RecentlyAddedTasks {
  private val _batch = MutableStateFlow<AddedBatch?>(null)
  val batch: StateFlow<AddedBatch?> = _batch.asStateFlow()

  fun record(thingId: String, taskIds: List<String>) {
    if (taskIds.isEmpty()) return
    _batch.value = AddedBatch(thingId, taskIds)
  }

  /**
   * The batch waiting for [thingId], which is then gone: whoever takes it says it, so it is said
   * once. Null when there is none, or it is another Thing's.
   */
  fun take(thingId: String): AddedBatch? =
    _batch.getAndUpdate { if (it?.thingId == thingId) null else it }
      ?.takeIf { it.thingId == thingId }
}

/** One acceptance's tasks on [thingId]. */
data class AddedBatch(
  val thingId: String,
  val taskIds: List<String>,
)
