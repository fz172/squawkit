package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Engine
import dev.fanfly.wingslog.thing.Propeller
import dev.fanfly.wingslog.thing.PropellerBlade
import dev.fanfly.wingslog.thing.PropellerHub
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * The accessors readers and the form use instead of the transitional fields (#668).
 *
 * [withDerivedComponentIds] carries the most weight here. Component ids are the join key logs,
 * tasks and squawks use to point at a component, so a derivation that drifts — between this and
 * `ThingInflater`, or between two runs over the same Thing — silently repoints every one of them.
 */
class ThingSpecAccessTest {

  private fun twin() = Thing(
    id = "thing-1",
    make = "Cessna",
    model = "310",
    serial = "SN-1",
    tail_number = "N123AB",
    engine = listOf(
      Engine(
        make = "Continental",
        propeller = Propeller(
          hub = PropellerHub(serial = "H-1"),
          blades = listOf(PropellerBlade(serial = "B-1"), PropellerBlade(serial = "B-2")),
        ),
      ),
      Engine(make = "Continental"),
    ),
  )

  // ---- spec ----

  @Test
  fun specValueReadsByKeyAndIsEmptyWhenAbsent() {
    val thing = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE)

    assertThat(thing.specValue(SpecKeys.MAKE)).isEqualTo("Cessna")
    assertThat(thing.specValue("vin")).isEmpty()
    assertThat(thing.hasSpec(SpecKeys.MAKE)).isTrue()
    assertThat(thing.hasSpec("vin")).isFalse()
  }

  @Test
  fun withSpecReplacesInPlaceRatherThanAppendingADuplicate() {
    val thing = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE)

    val edited = thing.withSpec(SpecKeys.MAKE, "Beechcraft")

    assertThat(edited.spec.count { it.key == SpecKeys.MAKE }).isEqualTo(1)
    assertThat(edited.specValue(SpecKeys.MAKE)).isEqualTo("Beechcraft")
    // The others are untouched.
    assertThat(edited.specValue(SpecKeys.MODEL)).isEqualTo("310")
  }

  @Test
  fun clearingASpecRemovesItRatherThanStoringABlank() {
    // ThingInflater and the backend cutover both drop empty values, so a Thing edited to clear its
    // serial has to end up in the same shape as one that never had one — otherwise two Things with
    // no serial compare unequal.
    val thing = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE)

    val cleared = thing.withSpec(SpecKeys.SERIAL, "")

    assertThat(cleared.spec.map { it.key }).doesNotContain(SpecKeys.SERIAL)
  }

  // ---- component navigation ----

  @Test
  fun slotNavigationFindsChildrenAtTheRightLevel() {
    val thing = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE)

    val airframe = thing.rootComponentInSlot(SlotKeys.AIRFRAME)
    assertThat(airframe).isNotNull()

    val engines = airframe!!.childrenInSlot(SlotKeys.ENGINE)
    assertThat(engines).hasSize(2)

    val propeller = engines[0].childInSlot(SlotKeys.PROPELLER)
    assertThat(propeller).isNotNull()
    assertThat(propeller!!.childrenInSlot(SlotKeys.BLADE)).hasSize(2)
    // The second engine has no propeller at all.
    assertThat(engines[1].childInSlot(SlotKeys.PROPELLER)).isNull()
  }

  @Test
  fun allComponentsInSlotSearchesTheWholeTree() {
    val thing = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE)

    // Engines are one level down and blades three, but both are found without navigating.
    assertThat(thing.allComponentsInSlot(SlotKeys.ENGINE)).hasSize(2)
    assertThat(thing.allComponentsInSlot(SlotKeys.BLADE)).hasSize(2)
    assertThat(thing.allComponentsInSlot(SlotKeys.PROPELLER)).hasSize(1)
  }

  @Test
  fun navigationOnAnEmptyTreeIsEmptyRatherThanThrowing() {
    val bare = Thing(id = "t")

    assertThat(bare.rootComponentInSlot(SlotKeys.AIRFRAME)).isNull()
    assertThat(bare.allComponentsInSlot(SlotKeys.ENGINE)).isEmpty()
  }

  // ---- id derivation ----

  @Test
  fun derivedIdsMatchWhatTheInflaterProduces() {
    // The two must agree exactly. If the form's tree is re-derived differently from the inflater's,
    // saving an edited Thing renumbers its components and every log pointing at one is orphaned.
    val inflated = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE)

    val reDerived = inflated.withDerivedComponentIds()

    assertThat(reDerived.components).isEqualTo(inflated.components)
  }

  @Test
  fun idsAreDerivedFromPositionSoTheDerivationIsStable() {
    val thing = Thing(
      id = "t-9",
      components = listOf(
        Component(
          slot_key = "airframe",
          children = listOf(
            Component(slot_key = "engine"),
            Component(slot_key = "engine"),
          ),
        ),
      ),
    )

    val derived = thing.withDerivedComponentIds()

    val airframe = derived.components.single()
    assertThat(airframe.id).isEqualTo("t-9:airframe.0")
    assertThat(airframe.children.map { it.id })
      .containsExactly("t-9:engine.0", "t-9:engine.1").inOrder()
  }

  @Test
  fun siblingsAreNumberedPerSlotNotPerPosition() {
    // A hub beside a blade must not push the blade's index along, or a propeller's first blade
    // becomes "blade.1" and stops matching the id its logs already point at.
    // Nested under an engine, which is the only shape that occurs — a propeller is never a root,
    // and the root-exclusion rule above would otherwise be what this measured.
    val thing = Thing(
      id = "t-9",
      components = listOf(
        Component(
          slot_key = "engine",
          children = listOf(
            Component(
              slot_key = "propeller",
              children = listOf(
                Component(slot_key = "hub"),
                Component(slot_key = "blade"),
                Component(slot_key = "blade"),
              ),
            ),
          ),
        ),
      ),
    )

    val propeller = thing.withDerivedComponentIds().components.single().children.single()

    assertThat(propeller.children.map { it.id }).containsExactly(
      "t-9:propeller.0.hub.0",
      "t-9:propeller.0.blade.0",
      "t-9:propeller.0.blade.1",
    ).inOrder()
  }

  @Test
  fun bladesUnderDifferentPropellersDoNotCollide() {
    // The path prefix is what keeps them apart — this is the case componentId's KDoc calls out.
    val thing = ThingInflater.inflate(
      Thing(
        id = "t-2",
        engine = listOf(
          Engine(propeller = Propeller(blades = listOf(PropellerBlade()))),
          Engine(propeller = Propeller(blades = listOf(PropellerBlade()))),
        ),
      ),
      AirplaneTemplate.TEMPLATE,
    )

    val bladeIds = thing.allComponentsInSlot(SlotKeys.BLADE).map { it.id }

    assertThat(bladeIds).containsExactly(
      "t-2:engine.0.propeller.0.blade.0",
      "t-2:engine.1.propeller.0.blade.0",
    )
    assertThat(bladeIds.toSet()).hasSize(2)
  }

  @Test
  fun derivationIsIdempotent() {
    val once = ThingInflater.inflate(twin(), AirplaneTemplate.TEMPLATE).withDerivedComponentIds()

    assertThat(once.withDerivedComponentIds()).isEqualTo(once)
  }

  @Test
  fun aThingWithNoIdYetProducesIdsItWillNotKeep() {
    // The constraint that shapes the form's design: ids embed the Thing id, which does not exist
    // until save. This documents the consequence rather than pretending it does not exist — the
    // form edits an id-less tree and FleetManagerImpl re-derives after assigning the id.
    val unsaved = Thing(id = "", components = listOf(Component(slot_key = "airframe")))

    val derived = unsaved.withDerivedComponentIds()

    assertThat(derived.components.single().id).isEqualTo(":airframe.0")
  }
}
