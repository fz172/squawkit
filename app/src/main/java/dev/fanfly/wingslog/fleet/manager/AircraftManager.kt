package dev.fanfly.wingslog.fleet.manager

import dev.fanfly.wingslog.aircraft.Aircraft

interface AircraftManager {
  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>

}