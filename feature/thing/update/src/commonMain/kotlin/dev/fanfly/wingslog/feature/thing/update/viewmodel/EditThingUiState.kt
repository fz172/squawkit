package dev.fanfly.wingslog.feature.thing.update.viewmodel

import dev.fanfly.wingslog.thing.PropellerHub
import dev.fanfly.wingslog.thing.Thing

data class EditThingUiState(
  val thing: Thing = Thing(),
  val initialAircraft: Thing? = null,
  val isLoading: Boolean = true,
  val isSaved: Boolean = false,
  val isDeleted: Boolean = false,
  val showValidationErrors: Boolean = false,
  /**
   * Only the account the thing *lives under* may delete it. A co-owner holds the same `OWNER`
   * role and may edit, but deleting tears down the whole share for everyone (§3.3), so it stays with
   * the host. Defaults false: a screen that hasn't resolved ownership yet must not offer Delete.
   */
  val hostedByMe: Boolean = false,
  /**
   * How many *other* people lose this thing if it is deleted. Deleting tears the share down for
   * all of them (PRD D5), so the confirmation says so rather than a generic "cannot be undone".
   */
  val otherMemberCount: Int = 0,
  /**
   * Whether this thing's template asks for serial numbers (PRD §4.8, `component_serial_prompt`).
   *
   * **Gates the validation, not only the fields.** Hiding a required input without relaxing its
   * rule is worse than leaving it: the form would refuse to save and give no reason, because the
   * field the user is being blocked on is not on screen. Defaults true, which is what shipped.
   */
  val requireSerials: Boolean = true,
) {
  /** Deleting is the hosting owner's call alone; rules enforce it, this keeps the UI honest. */
  val canDelete: Boolean get() = hostedByMe && thing.id.isNotEmpty()

  val hasChanges: Boolean
    get() = initialAircraft != null && thing != initialAircraft

  val isValid: Boolean
    get() {
      if (thing.make.isBlank() || thing.model.isBlank()) return false
      if (requireSerials && thing.serial.isBlank()) return false
      thing.engine.forEach { engine ->
        if (engine.make.isBlank() || engine.model.isBlank()) return false
        if (requireSerials && engine.serial.isBlank()) return false
        val hub = engine.propeller?.hub ?: PropellerHub()
        if (hub.make.isBlank() || hub.model.isBlank()) return false
        // The hub's own serial is shown with an error indicator but has never been enforced here.
        // Left as it was rather than fixed in passing: making it required would start rejecting
        // saves that succeed today, which is a product change and not this commit's business.
        // See the #659 discussion.
        if (requireSerials) {
          engine.propeller?.blades?.forEach { blade ->
            if (blade.serial.isBlank()) return false
          }
        }
      }
      return true
    }
}