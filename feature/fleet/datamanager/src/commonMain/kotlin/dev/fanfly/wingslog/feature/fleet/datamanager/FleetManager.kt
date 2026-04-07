package dev.fanfly.wingslog.feature.fleet.datamanager

import dev.fanfly.wingslog.aircraft.Aircraft
import kotlinx.coroutines.flow.Flow

interface FleetManager {
  /**
   * Loads the current user's aircraft from the data source.
   */
  fun observeFleetDashboard(): Flow<List<Aircraft>>

  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>

  fun loadAircraft(id: String): Flow<Aircraft?>

  suspend fun deleteAircraft(id: String): Result<Boolean>
}
