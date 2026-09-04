package dev.fanfly.wingslog.feature.thing.update.viewmodel

import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.componentsMissingSerials
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate

data class EditThingUiState(
  val thing: Thing = Thing(),
  val initialThing: Thing? = null,
  val isLoading: Boolean = true,
  val isSaved: Boolean = false,
  /**
   * Set alongside [isSaved] when the create should continue into the starter-pack step rather
   * than close: the id of the Thing just written, whose DNA carries the pack.
   */
  val starterPackThingId: String? = null,
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
  /**
   * The template being edited under, which is what says which fields exist at all.
   *
   * Null only before the load resolves, when there is nothing to validate yet.
   */
  val template: ThingTemplate? = null,
  /**
   * The words [template] is described in.
   *
   * Carried on the state rather than read from `LocalThingLexicon`, because that one is provided
   * app-wide from the *selected* Thing: on a create the form would title itself after whatever the
   * switcher happens to be pointing at, not the type just picked.
   */
  val lexicon: Lexicon = GenericLexicon.LEXICON,
) {
  /** Deleting is the hosting owner's call alone; rules enforce it, this keeps the UI honest. */
  val canDelete: Boolean get() = hostedByMe && thing.id.isNotEmpty()

  val hasChanges: Boolean
    get() = initialThing != null && thing != initialThing

  /**
   * **A field is required when it is on screen and the template asks for it.**
   *
   * Two rules, both walking what the template declares rather than an airplane:
   *
   * - a `SpecField` marked `required` has a value;
   * - a component that is *present* has a serial, where its slot expects one.
   *
   * Present is the operative word for the second. A slot with no component has nothing to validate
   * — a car with no engine recorded is complete, not invalid — so the check reads what is stored,
   * not what could be.
   *
   * [requireSerials] relaxes both. Hiding a required input without relaxing its rule refuses the
   * save and gives no reason, because the field being blocked on is not on screen.
   */
  val isValid: Boolean
    get() {
      val specOk = template?.spec_fields.orEmpty()
        .all { field ->
          val hidden = field.key == SpecKeys.SERIAL && !requireSerials
          !field.required || hidden || thing.specValue(field.key)
            .isNotBlank()
        }
      if (!specOk) return false
      if (!requireSerials) return true
      return template.componentsMissingSerials(thing)
        .isEmpty()
    }
}
