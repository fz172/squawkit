package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlinx.coroutines.flow.Flow

interface TaskDataManager {

  /**
   * Observe all task cards for an thing in real-time.
   */
  fun observeTasks(thingId: String): Flow<List<MaintenanceTask>>

  /**
   * Add a new task card to an thing.
   */
  suspend fun addTask(
    thingId: String,
    card: MaintenanceTask,
  ): Result<Boolean>

  /**
   * Update an existing task card.
   */
  suspend fun updateTask(
    thingId: String,
    card: MaintenanceTask,
  ): Result<Boolean>

  /**
   * Delete a task card. Logs that reference the card's ID will have orphaned IDs,
   * which are silently ignored during display.
   */
  suspend fun deleteTask(
    thingId: String,
    cardId: String,
  ): Result<Boolean>
}
