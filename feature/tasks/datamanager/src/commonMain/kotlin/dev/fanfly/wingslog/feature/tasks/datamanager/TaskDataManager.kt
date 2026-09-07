package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlinx.coroutines.flow.Flow

interface TaskDataManager {

  /**
   * Observe all task cards for a thing in real-time.
   */
  fun observeTasks(thingId: String): Flow<List<MaintenanceTask>>

  /**
   * Add a new task card to a thing.
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
   * Marks [card]'s current cycle complete without a log, persisting against the card as last
   * saved. Clears any reschedule override in the same write: TaskDueManager resolves force-due
   * overrides before it looks at force-complied state, so a skip left alongside an override would
   * never move the next due. [currentReading] is the reading of the card's default meter.
   */
  suspend fun skipCycle(
    thingId: String,
    card: MaintenanceTask,
    currentReading: Float,
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
