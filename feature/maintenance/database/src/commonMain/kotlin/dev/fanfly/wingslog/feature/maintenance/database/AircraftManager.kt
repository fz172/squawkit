package dev.fanfly.wingslog.feature.maintenance.database

import dev.fanfly.wingslog.aircraft.Aircraft
import kotlinx.coroutines.flow.Flow

interface AircraftManager {
  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>

  fun loadAircraft(id: String): Flow<Aircraft?>

  suspend fun deleteAircraft(id: String): Result<Boolean>

}