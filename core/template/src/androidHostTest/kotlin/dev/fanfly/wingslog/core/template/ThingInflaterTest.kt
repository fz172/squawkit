package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/** What the inflater still does now that the form writes spec and components itself (#668). */
class ThingInflaterTest {

  private val airplane = AirplaneTemplate.TEMPLATE

  private fun thing(vararg spec: Pair<String, String>) = Thing(
    id = "thing-1",
    spec = spec.map { (key, value) -> Spec(key = key, value_ = value) },
  )

  @Test
  fun componentIdMatchesTheBackendDerivation() {
    // Transcribed from thingPayloads.ts; withDerivedComponentIds still calls this.
    assertThat(ThingInflater.componentId("thing-1", listOf("airframe", "0")))
      .isEqualTo("thing-1:airframe.0")
    assertThat(
      ThingInflater.componentId(
        "thing-1",
        listOf(
          "engine",
          "1",
          "propeller",
          "0",
          "blade",
          "2"
        )
      ),
    ).isEqualTo("thing-1:engine.1.propeller.0.blade.2")
  }

  @Test
  fun nameFallsBackFromTailNumberToMakeAndModel() {
    // PRD §9.1's order, read from spec now.
    assertThat(
      ThingInflater.inflate(
        thing(SpecKeys.TAIL_NUMBER to "N123AB", SpecKeys.MAKE to "Cessna"),
        airplane,
      ).name,
    ).isEqualTo("N123AB")

    assertThat(
      ThingInflater.inflate(
        thing(SpecKeys.MAKE to "Cessna", SpecKeys.MODEL to "310"),
        airplane,
      ).name,
    ).isEqualTo("Cessna 310")

    assertThat(ThingInflater.inflate(Thing(id = "t"), airplane).name).isEmpty()
  }

  @Test
  fun anExistingNameIsNeverOverwritten() {
    // The user named it. Regenerating from make/model on every write would rename it back.
    val named = thing(SpecKeys.MAKE to "Cessna").copy(name = "The Old Girl")

    assertThat(
      ThingInflater.inflate(
        named,
        airplane
      ).name
    ).isEqualTo("The Old Girl")
  }

  @Test
  fun specAndComponentsPassThroughUntouched() {
    // Deriving here would silently discard whatever the form wrote.
    val edited = Thing(
      id = "t",
      spec = listOf(Spec(key = SpecKeys.MAKE, value_ = "Beechcraft")),
      components = listOf(Component(slot_key = SlotKeys.AIRFRAME)),
    )

    val inflated = ThingInflater.inflate(edited, airplane)

    assertThat(inflated.spec).isEqualTo(edited.spec)
    assertThat(inflated.components).isEqualTo(edited.components)
  }

  @Test
  fun dnaIsWrittenWhenAbsent() {
    // Migrated Things have no DNA — the cutover predates field 12.
    val migrated = thing(SpecKeys.MAKE to "Cessna")
    assertThat(migrated.template).isNull()

    // Everything but the words: the lexicon is app UI and is resolved by template id at render,
    // so storing a copy would fork the app's vocabulary into user data (see LexiconOwnershipTest).
    assertThat(ThingInflater.inflate(migrated, airplane).template).isEqualTo(
      airplane.copy(lexicon = null)
    )
  }

  @Test
  fun existingDnaIsNeverReplaced() {
    // A Thing created from another template must not have its DNA overwritten by the fallback.
    val car = airplane.copy(id = "car", display_name = "Car")

    val result = ThingInflater.inflate(thing().copy(template = car), airplane)

    assertThat(result.template?.id).isEqualTo("car")
  }

  @Test
  fun inflationIsIdempotent() {
    val once = ThingInflater.inflate(thing(SpecKeys.MAKE to "Cessna"), airplane)

    assertThat(ThingInflater.inflate(once, airplane)).isEqualTo(once)
  }
}
