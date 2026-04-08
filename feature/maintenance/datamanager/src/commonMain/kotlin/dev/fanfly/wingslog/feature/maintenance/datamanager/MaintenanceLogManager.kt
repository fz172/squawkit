package dev.fanfly.wingslog.feature.maintenance.datamanager

import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import kotlinx.coroutines.flow.Flow

interface MaintenanceLogManager {
  /**
   * Observes the list of maintenance logs for a specific aircraft.
   */
  fun observeLogs(aircraftId: String): Flow<List<MaintenanceLog>>

  /**
   * Observes the maintenance overview (summary stats) for a specific aircraft.
   */
  fun observeMaintenanceOverview(aircraftId: String): Flow<MaintenanceOverview?>

  /**
   * Adds a new maintenance log for an aircraft.
   */
  suspend fun addLog(aircraftId: String, log: MaintenanceLog): Result<Boolean>

  /**
   * Updates an existing maintenance log.
   */
  suspend fun updateLog(aircraftId: String, log: MaintenanceLog): Result<Boolean>

  /**
   * Deletes a maintenance log.
   */
  suspend fun deleteLog(aircraftId: String, logId: String): Result<Boolean>
}
