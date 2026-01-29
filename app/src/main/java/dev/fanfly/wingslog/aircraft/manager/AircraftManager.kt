package dev.fanfly.wingslog.aircraft.manager

import dev.fanfly.wingslog.aircraft.Aircraft
import kotlinx.coroutines.flow.Flow

interface AircraftManager {
  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>

  fun loadAircraft(id: String): Flow<Aircraft?>

}