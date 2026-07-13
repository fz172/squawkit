package dev.fanfly.wingslog.feature.aircraft.update.viewmodel

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.PropellerHub

data class EditAircraftUiState(
  val aircraft: Aircraft = Aircraft(),
  val initialAircraft: Aircraft? = null,
  val isLoading: Boolean = true,
  val isSaved: Boolean = false,
  val isDeleted: Boolean = false,
  val showValidationErrors: Boolean = false,
  /**
   * Only the account the aircraft *lives under* may delete it. A co-owner holds the same `OWNER`
   * role and may edit, but deleting tears down the whole share for everyone (§3.3), so it stays with
   * the host. Defaults false: a screen that hasn't resolved ownership yet must not offer Delete.
   */
  val hostedByMe: Boolean = false,
  /**
   * How many *other* people lose this aircraft if it is deleted. Deleting tears the share down for
   * all of them (PRD D5), so the confirmation says so rather than a generic "cannot be undone".
   */
  val otherMemberCount: Int = 0,
) {
  /** Deleting is the hosting owner's call alone; rules enforce it, this keeps the UI honest. */
  val canDelete: Boolean get() = hostedByMe && aircraft.id.isNotEmpty()

  val hasChanges: Boolean
    get() = initialAircraft != null && aircraft != initialAircraft

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