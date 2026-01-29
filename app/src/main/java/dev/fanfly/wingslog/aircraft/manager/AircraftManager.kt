package dev.fanfly.wingslog.aircraft.manager

import dev.fanfly.wingslog.aircraft.Aircraft

interface AircraftManager {
  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>

}