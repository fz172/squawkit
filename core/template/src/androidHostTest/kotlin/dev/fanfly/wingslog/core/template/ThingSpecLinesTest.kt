package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * The Thing's own spec, split into a headline and its labelled identifiers.
 *
 * The bug this replaces: an airplane declares two identifiers, the card captioned one line "S/N"
 * and ran everything else together, so a tail number was shown as a serial and the serial rode
 * along in the make/model run as if it were part of the model name.
 */
class ThingSpecLinesTest {

  private val airplane = AirplaneTemplate.TEMPLATE

  private fun thing(vararg spec: Pair<String, String>) = Thing(
    id = "t",
    spec = spec.map { (key, value) -> Spec(key = key, value_ = value) },
  )

  @Test
  fun everyIdentifierIsShownBesideTheTemplatesWordForIt() {
    val lines = airplane.specLines(
      thing(
        SpecKeys.MAKE to "Sling",
        SpecKeys.MODEL to "TSi",
        SpecKeys.SERIAL to "532SK",
        SpecKeys.TAIL_NUMBER to "N532SL",
      ),
    )

    // Make and model name what it is; neither identifier is allowed into that run.
    assertThat(lines.headline).isEqualTo("Sling TSi")
    // The tail number leads — it is what an owner calls the aeroplane by — and each value carries
    // the template's own label, so neither can be read as the other.
    assertThat(lines.identifiers.map { it.label })
      .containsExactly("Tail Number", "Serial Number")
      .inOrder()
    assertThat(lines.identifiers.map { it.value })
      .containsExactly("N532SL", "532SK")
      .inOrder()
  }

  @Test
  fun anUnfilledIdentifierIsDroppedRatherThanLabelled() {
    val lines = airplane.specLines(
      thing(SpecKeys.MAKE to "Sling", SpecKeys.TAIL_NUMBER to "N532SL"),
    )

    // "Serial Number:" with nothing after it reads as a load that failed, not as a blank field.
    assertThat(lines.identifiers.map { it.label }).containsExactly("Tail Number")
    assertThat(lines.headline).isEqualTo("Sling")
  }

  @Test
  fun aPresetWithOneIdentifierLabelsItInItsOwnWords() {
    val car = CanonicalTemplates.AUTOMOTIVE.specLines(
      thing(
        "make" to "Honda",
        "model" to "Civic",
        "year" to "2019",
        "vin" to "1HGBH41JXMN109186",
      ),
    )

    // Not "S/N" — the label comes from the template, so a car says VIN and a boat says Hull ID
    // without this code knowing either exists.
    assertThat(car.headline).isEqualTo("Honda Civic 2019")
    assertThat(car.identifiers.map { it.label }).containsExactly("VIN")

    val boat = CanonicalTemplates.BOAT.specLines(thing("hull_id" to "ABC12345D616"))
    assertThat(boat.identifiers.map { it.label }).containsExactly("Hull ID")
  }

  @Test
  fun aPresetWithNoIdentifierShowsOnlyWhatItDeclares() {
    // The load-bearing case: a home has no make, no model and no serial, so an identifier line
    // would be an aviation assumption showing through.
    val home = CanonicalTemplates.HOME.specLines(
      thing("address" to "742 Evergreen Terrace", "year_built" to "1974"),
    )

    assertThat(home.headline).isEqualTo("742 Evergreen Terrace 1974")
    assertThat(home.identifiers).isEmpty()
    assertThat(home.isEmpty).isFalse()
  }

  @Test
  fun aTemplateDeclaringNoSpecFieldsYieldsNothingToDraw() {
    // `custom` is the floor, and a null template is the account-level screen with nothing selected.
    assertThat(CanonicalTemplates.CUSTOM.specLines(thing("make" to "Sling")).isEmpty).isTrue()
    val nothingSelected: ThingTemplate? = null
    assertThat(nothingSelected.specLines(thing(SpecKeys.MAKE to "Sling")).isEmpty).isTrue()
  }
}
