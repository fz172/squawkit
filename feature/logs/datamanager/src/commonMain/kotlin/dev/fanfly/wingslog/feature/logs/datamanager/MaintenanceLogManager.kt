package dev.fanfly.wingslog.feature.logs.datamanager

import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.ManualMeterReading
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
   * The readings set by hand on the dashboard, at most one per meter (#1368).
   *
   * For a caller that works out the current reading itself from a list of logs it already holds —
   * the due computation does. Anything else wants [observeCurrentReadings].
   */
  fun observeManualReadings(thingId: String): Flow<List<ManualMeterReading>>

  /**
   * What each meter reads now: the most recent of the logs’ readings and the manual ones, as
   * [currentReadingStates] decides it.
   *
   * Worked out from the records rather than read off the stored overview. The overview is only as
   * current as the last client to rebuild it, and a client that predates manual readings rebuilds
   * it without them.
   */
  fun observeCurrentReadings(thingId: String): Flow<List<CurrentReading>>

  /**
   * Sets what [meterKey] reads now, replacing any reading set this way before.
   *
   * It becomes the current reading until a log dated later, or saved later the same day, records
   * the meter. Not refused when it is lower than the current one: a corrected typo and a replaced
   * tach both legitimately go down, and warning about it is the caller’s job.
   */
  suspend fun setManualReading(
    thingId: String,
    meterKey: String,
    value: Double,
  ): Result<Boolean>

  /**
   * Adds a new maintenance log for a thing, stamping when its readings were saved.
   */
  suspend fun addLog(thingId: String, log: MaintenanceLog): Result<Boolean>

  /**
   * Updates an existing maintenance log. Its readings keep the time they were first saved unless
   * this edit changes one — fixing a typo in the description does not make an old reading new.
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
