package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import kotlinx.coroutines.flow.Flow

/** The one place due status is computed. Every screen that shows it reads from here. */
interface TaskStatusManager {
  /** Every task with its due status: active ones first, most urgent first, then complied. */
  fun observeTasksWithStatus(thingId: String): Flow<List<MaintenanceTaskWithStatus>>

  /** Re-evaluates against the clock; call when the app comes back to the foreground. */
  fun refreshDueStatus()
}
