package dev.fanfly.wingslog.feature.aircraft.edit.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.aircraft

data class EditAircraftUiState(
  val aircraft: Aircraft = aircraft {},
  val isLoading: Boolean = true,
  val isSaved: Boolean = false,
  val showValidationErrors: Boolean = false
) {
  val isValid: Boolean
    get() {
      if (aircraft.make.isBlank() || aircraft.model.isBlank() || aircraft.serial.isBlank()) return false
      aircraft.engineList.forEach { engine ->
        if (engine.make.isBlank() || engine.model.isBlank() || engine.serial.isBlank()) return false
        if (engine.propeller.hub.make.isBlank() || engine.propeller.hub.model.isBlank()) return false
        engine.propeller.bladesList.forEach { blade ->
          if (blade.serial.isBlank()) return false
        }
      }
      return true
    }
}