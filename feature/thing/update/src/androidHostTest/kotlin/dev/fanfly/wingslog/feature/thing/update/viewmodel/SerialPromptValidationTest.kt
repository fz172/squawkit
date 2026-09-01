package dev.fanfly.wingslog.feature.thing.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * That turning off `component_serial_prompt` relaxes the validation as well as hiding the fields.
 *
 * **This is the failure the gate would otherwise introduce.** Four serial inputs are required —
 * airframe, engine, propeller hub, blades — and `isValid` enforces three of them. Hiding the inputs
 * without relaxing the rule leaves a form that refuses to save and cannot say why, because the field
 * the user is blocked on is not on screen. That is strictly worse than showing a serial box to a
 * homeowner.
 *
 * The airplane template sets the capability true, so none of this is reachable from the shipped
 * preset — which is exactly why it is asserted rather than assumed.
 *
 * **The hub's serial is now enforced, and was not before.** The old hardcoded validation skipped it
 * deliberately — "making it required would start rejecting saves that succeed today" — but the rule
 * is now the template's: a component that is present, whose slot expects a serial, must have one.
 * The hub qualifies on both counts, so the exception went with the hardcoding (#729).
 */
class SerialPromptValidationTest {

  /** Spec plus the component tree, in the hierarchy the parts actually attach in (#729). */
  private fun thing(serials: Boolean) = Thing(
    spec = listOf(
      Spec(key = SpecKeys.MAKE, value_ = "Cessna"),
      Spec(key = SpecKeys.MODEL, value_ = "172"),
      Spec(key = SpecKeys.SERIAL, value_ = if (serials) "SN-1" else ""),
    ),
    // Engines at the root — there is no airframe component, because the airframe is the thing and
    // its identity is the spec above. The propeller carries what the hub used to.
    components = listOf(
      Component(
        slot_key = SlotKeys.ENGINE,
        make = "Lycoming",
        model = "O-320",
        serial = if (serials) "E-1" else "",
        children = listOf(
          Component(
            slot_key = SlotKeys.PROPELLER,
            make = "McCauley",
            model = "1C160",
            serial = if (serials) "P-1" else "",
            children = listOf(
              Component(
                slot_key = SlotKeys.BLADE,
                serial = if (serials) "B-1" else "",
              ),
            ),
          ),
        ),
      ),
    ),
  )

  @Test
  fun theFailOpenDefaultStillAsksForSerials() {
    // #660: a wrong default must not silently stop asking aviation users for serial numbers —
    // which would let a Thing be created without the identifiers its logbook depends on.
    val state = EditThingUiState(
      thing = thing(serials = false),
      requireSerials = CurrentThingTemplate.ALL_ENABLED.component_serial_prompt,
      template = AirplaneTemplate.TEMPLATE,
    )

    assertThat(state.isValid).isFalse()
  }

  @Test
  fun blankSerialsBlockSavingWhenTheTemplateAsksForThem() {
    val state =
      EditThingUiState(
        thing = thing(serials = false),
        requireSerials = true,
        template = AirplaneTemplate.TEMPLATE,
      )

    assertThat(state.isValid).isFalse()
  }

  @Test
  fun blankSerialsDoNotBlockSavingWhenTheTemplateDoesNot() {
    val state =
      EditThingUiState(
        thing = thing(serials = false),
        requireSerials = false,
        template = AirplaneTemplate.TEMPLATE,
      )

    assertThat(state.isValid).isTrue()
  }

  @Test
  fun makeAndModelStayRequiredEitherWay() {
    // Only the serials are template-controlled. A thing with no make or model is unusable whatever
    // it is, so relaxing serials must not relax everything alongside it.
    val nameless =
      Thing(spec = listOf(Spec(key = SpecKeys.SERIAL, value_ = "SN-1")))

    assertThat(
      EditThingUiState(
        thing = nameless,
        requireSerials = false,
        template = AirplaneTemplate.TEMPLATE,
      ).isValid
    ).isFalse()
    assertThat(
      EditThingUiState(
        thing = nameless,
        requireSerials = true,
        template = AirplaneTemplate.TEMPLATE,
      ).isValid
    ).isFalse()
  }

  @Test
  fun theDefaultIsWhatShipped() {
    // Phase 2's acceptance criterion: a state constructed without the flag behaves as before.
    assertThat(
      EditThingUiState(
        thing = thing(serials = false),
        template = AirplaneTemplate.TEMPLATE,
      ).isValid
    ).isFalse()
    assertThat(
      EditThingUiState(
        thing = thing(serials = true),
        template = AirplaneTemplate.TEMPLATE,
      ).isValid
    ).isTrue()
  }

  @Test
  fun loadingAThingDoesNotClearWhatOtherFlowsOwn() {
    // Delete vanished from the edit screen because the load rebuilt the state from scratch, and
    // `hostedByMe` — which arrives from its own collector, before or after the load — went back to
    // false with it. Only the hosting owner may delete, so false hides the button entirely.
    val loaded = EditThingUiState(
      hostedByMe = true,
      otherMemberCount = 3,
      template = AirplaneTemplate.TEMPLATE,
    ).copy(thing = thing(serials = true))

    assertThat(loaded.hostedByMe).isTrue()
    assertThat(loaded.otherMemberCount).isEqualTo(3)
  }
}
