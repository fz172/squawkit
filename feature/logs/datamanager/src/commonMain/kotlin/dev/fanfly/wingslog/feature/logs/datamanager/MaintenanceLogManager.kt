package dev.fanfly.wingslog.feature.logs.datamanager

import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import kotlinx.coroutines.flow.Flow

interface MaintenanceLogManager {
  /**
   * Observes the list of maintenance logs for a specific thing.
   */
  fun observeLogs(thingId: String): Flow<List<MaintenanceLog>>

  /**
   * Log id → uid of the account that wrote the latest revision (design §7.5).
   *
   * Kept separate from [observeLogs] because authorship lives in the sync envelope, not the proto
   * payload — that is precisely what makes it unforgeable. Null for a log whose author we have never
   * seen (written before the field existed).
   */
  fun observeLogAuthors(thingId: String): Flow<Map<String, String?>>

  /**
   * Observes the maintenance overview (summary stats) for a specific thing.
   */
  fun observeMaintenanceOverview(thingId: String): Flow<MaintenanceOverview?>

  /**
   * Adds a new maintenance log for an thing.
   */
  suspend fun addLog(thingId: String, log: MaintenanceLog): Result<Boolean>

  /**
   * Updates an existing maintenance log.
   */
  suspend fun updateLog(
    thingId: String,
    log: MaintenanceLog
  ): Result<Boolean>

  /**
   * Deletes a maintenance log.
   */
  suspend fun deleteLog(thingId: String, logId: String): Result<Boolean>
}
