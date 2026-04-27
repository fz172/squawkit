package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.aircraft.MaintenanceTask
import kotlinx.coroutines.flow.Flow

interface TaskDataManager {

  /**
   * Observe all task cards for an aircraft in real-time.
   */
  fun observeTasks(aircraftId: String): Flow<List<MaintenanceTask>>

  /**
   * Add a new task card to an aircraft.
   */
  suspend fun addTask(
    aircraftId: String,
    card: MaintenanceTask,
  ): Result<Boolean>

  /**
   * Update an existing task card.
   */
  suspend fun updateTask(
    aircraftId: String,
    card: MaintenanceTask,
  ): Result<Boolean>

  /**
   * Delete a task card. Logs that reference the card's ID will have orphaned IDs,
   * which are silently ignored during display.
   */
  suspend fun deleteTask(
    aircraftId: String,
    cardId: String,
  ): Result<Boolean>
}
