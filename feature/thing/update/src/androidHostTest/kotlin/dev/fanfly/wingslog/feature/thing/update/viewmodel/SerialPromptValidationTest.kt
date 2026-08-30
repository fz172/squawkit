package dev.fanfly.wingslog.feature.thing.update.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Engine
import dev.fanfly.wingslog.thing.Propeller
import dev.fanfly.wingslog.thing.PropellerBlade
import dev.fanfly.wingslog.thing.PropellerHub
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
 */
class SerialPromptValidationTest {

  private fun thing(serials: Boolean) = Thing(
    make = "Cessna",
    model = "172",
    serial = if (serials) "SN-1" else "",
    engine = listOf(
      Engine(
        make = "Lycoming",
        model = "O-320",
        serial = if (serials) "E-1" else "",
        propeller = Propeller(
          hub = PropellerHub(make = "McCauley", model = "1C160"),
          blades = listOf(PropellerBlade(serial = if (serials) "B-1" else "")),
        ),
      ),
    ),
  )

  @Test
  fun blankSerialsBlockSavingWhenTheTemplateAsksForThem() {
    val state =
      EditThingUiState(thing = thing(serials = false), requireSerials = true)

    assertThat(state.isValid).isFalse()
  }

  @Test
  fun blankSerialsDoNotBlockSavingWhenTheTemplateDoesNot() {
    val state =
      EditThingUiState(thing = thing(serials = false), requireSerials = false)

    assertThat(state.isValid).isTrue()
  }

  @Test
  fun makeAndModelStayRequiredEitherWay() {
    // Only the serials are template-controlled. A thing with no make or model is unusable whatever
    // it is, so relaxing serials must not relax everything alongside it.
    val nameless = Thing(make = "", model = "", serial = "SN-1")

    assertThat(
      EditThingUiState(
        thing = nameless,
        requireSerials = false
      ).isValid
    ).isFalse()
    assertThat(
      EditThingUiState(
        thing = nameless,
        requireSerials = true
      ).isValid
    ).isFalse()
  }

  @Test
  fun theDefaultIsWhatShipped() {
    // Phase 2's acceptance criterion: a state constructed without the flag behaves as before.
    assertThat(EditThingUiState(thing = thing(serials = false)).isValid).isFalse()
    assertThat(EditThingUiState(thing = thing(serials = true)).isValid).isTrue()
  }
}
