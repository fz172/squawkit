package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * Whitespace a user typed, and what the app does with it.
 *
 * The bug: a make saved as "Sling " rendered "Sling  TSi" everywhere the two are joined — the
 * hero, the spec block, a component chip — a gap wide enough to read as a broken layout.
 */
class ThingTextTest {

  @Test
  fun aPhraseJoinsWithExactlyOneSpaceHoweverItsPartsWereTyped() {
    assertThat(listOf("Sling ", "TSi").joinAsPhrase()).isEqualTo("Sling TSi")
    assertThat(listOf("Sling", " TSi").joinAsPhrase()).isEqualTo("Sling TSi")
    assertThat(listOf("Rotax", "915  IS3A").joinAsPhrase()).isEqualTo("Rotax 915 IS3A")
  }

  @Test
  fun aBlankPartIsDroppedRatherThanJoined() {
    assertThat(listOf("Sling", "").joinAsPhrase()).isEqualTo("Sling")
    assertThat(listOf("", "  ", "TSi").joinAsPhrase()).isEqualTo("TSi")
    assertThat(listOf("", " ").joinAsPhrase()).isEmpty()
  }

  @Test
  fun aStoredValueIsCleanedOnTheWayIn() {
    val thing = Thing(
      id = "t",
      name = "  The Old Girl ",
      spec = listOf(
        Spec(key = SpecKeys.MAKE, value_ = "Sling "),
        Spec(key = SpecKeys.MODEL, value_ = "TSi"),
      ),
      components = listOf(
        Component(
          id = "t:engine.1",
          slot_key = SlotKeys.ENGINE,
          make = " Rotax",
          model = "915  IS3A",
          serial = "10010511 ",
          spec = listOf(Spec(key = "position", value_ = "Front ")),
          children = listOf(
            Component(
              id = "t:engine.1.propeller.1",
              slot_key = SlotKeys.PROPELLER,
              make = "Airmaster ",
              model = "AP430",
            ),
          ),
        ),
      ),
    ).withNormalisedText()

    assertThat(thing.name).isEqualTo("The Old Girl")
    assertThat(thing.specValue(SpecKeys.MAKE)).isEqualTo("Sling")
    val engine = thing.components.first()
    assertThat(engine.make).isEqualTo("Rotax")
    assertThat(engine.model).isEqualTo("915 IS3A")
    assertThat(engine.serial).isEqualTo("10010511")
    assertThat(engine.specValue("position")).isEqualTo("Front")
    assertThat(engine.children.first().make).isEqualTo("Airmaster")
  }

  /**
   * `withCustomSpec` keeps a row alive while either half is filled, and a just-added field is
   * labelled `" "` until the user types into it. Trimming that away at save would take the row
   * out from under them.
   */
  @Test
  fun aCustomFieldsLabelIsLeftExactlyAsItIs() {
    val thing = Thing(
      id = "t",
      spec = listOf(Spec(key = "custom_1", label = " ", value_ = "12 ")),
    ).withNormalisedText()

    assertThat(thing.spec.single().label).isEqualTo(" ")
    assertThat(thing.spec.single().value_).isEqualTo("12")
  }
}
