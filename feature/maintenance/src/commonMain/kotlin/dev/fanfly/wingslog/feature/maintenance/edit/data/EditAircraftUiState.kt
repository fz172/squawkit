package dev.fanfly.wingslog.feature.maintenance.edit.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.PropellerHub

data class EditAircraftUiState(
  val aircraft: Aircraft = Aircraft(),
  val isLoading: Boolean = true,
  val isSaved: Boolean = false,
  val isDeleted: Boolean = false,
  val showValidationErrors: Boolean = false,
) {
  val isValid: Boolean
    get() {
      if (aircraft.make.isBlank() || aircraft.model.isBlank() || aircraft.serial.isBlank()) return false
      aircraft.engine.forEach { engine ->
        if (engine.make.isBlank() || engine.model.isBlank() || engine.serial.isBlank()) return false
        val hub = engine.propeller?.hub ?: PropellerHub()
        if (hub.make.isBlank() || hub.model.isBlank()) return false
        engine.propeller?.blades?.forEach { blade ->
          if (blade.serial.isBlank()) return false
        }
      }
      return true
    }
}