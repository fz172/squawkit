package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * The accessors readers and the form use instead of the retired fields (#668).
 *
 * Ids are a stored join key, so a derivation that drifts repoints every log silently.
 */
class ThingSpecAccessTest {

  /** A twin, built the way the form and the generator build one — the tree is the source. */
  private fun twin() = Thing(
    id = "thing-1",
    spec = listOf(
      Spec(key = SpecKeys.MAKE, value_ = "Cessna"),
      Spec(key = SpecKeys.MODEL, value_ = "310"),
      Spec(key = SpecKeys.SERIAL, value_ = "SN-1"),
      Spec(key = SpecKeys.TAIL_NUMBER, value_ = "N123AB"),
    ),
    components = listOf(
      Component(
        slot_key = SlotKeys.AIRFRAME,
        make = "Cessna",
        children = listOf(
          Component(
            slot_key = SlotKeys.ENGINE,
            make = "Continental",
            children = listOf(
              Component(
                slot_key = SlotKeys.PROPELLER,
                children = listOf(
                  Component(slot_key = SlotKeys.HUB, serial = "H-1"),
                  Component(slot_key = SlotKeys.BLADE, serial = "B-1"),
                  Component(slot_key = SlotKeys.BLADE, serial = "B-2"),
                ),
              ),
            ),
          ),
          Component(slot_key = SlotKeys.ENGINE, make = "Continental"),
        ),
      ),
    ),
  ).withDerivedComponentIds()

  // ---- spec ----

  @Test
  fun specValueReadsByKeyAndIsEmptyWhenAbsent() {
    val thing = twin()

    assertThat(thing.specValue(SpecKeys.MAKE)).isEqualTo("Cessna")
    assertThat(thing.specValue("vin")).isEmpty()
    assertThat(thing.hasSpec(SpecKeys.MAKE)).isTrue()
    assertThat(thing.hasSpec("vin")).isFalse()
  }

  @Test
  fun withSpecReplacesInPlaceRatherThanAppendingADuplicate() {
    val thing = twin()

    val edited = thing.withSpec(SpecKeys.MAKE, "Beechcraft")

    assertThat(edited.spec.count { it.key == SpecKeys.MAKE }).isEqualTo(1)
    assertThat(edited.specValue(SpecKeys.MAKE)).isEqualTo("Beechcraft")
    // The others are untouched.
    assertThat(edited.specValue(SpecKeys.MODEL)).isEqualTo("310")
  }

  @Test
  fun clearingASpecRemovesItRatherThanStoringABlank() {
    // Empty values are dropped, so a cleared serial matches one that never existed.
    val thing = twin()

    val cleared = thing.withSpec(SpecKeys.SERIAL, "")

    assertThat(cleared.spec.map { it.key }).doesNotContain(SpecKeys.SERIAL)
  }

  // ---- component navigation ----

  @Test
  fun slotNavigationFindsChildrenAtTheRightLevel() {
    val thing = twin()

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
    val thing = twin()

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
  fun derivationIsStableAcrossRepeatedSaves() {
    // An unrelated edit must not renumber components.
    val once = twin()

    assertThat(once.withDerivedComponentIds().components).isEqualTo(once.components)
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
      .containsExactly("t-9:engine.0", "t-9:engine.1")
      .inOrder()
  }

  @Test
  fun siblingsAreNumberedPerSlotNotPerPosition() {
    // A hub beside a blade must not shift the blade's index. Nested under an engine, the only
    // shape that occurs — a root propeller would measure the root-exclusion rule instead.
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

    val propeller =
      thing.withDerivedComponentIds().components.single().children.single()

    assertThat(propeller.children.map { it.id }).containsExactly(
      "t-9:propeller.0.hub.0",
      "t-9:propeller.0.blade.0",
      "t-9:propeller.0.blade.1",
    )
      .inOrder()
  }

  @Test
  fun bladesUnderDifferentPropellersDoNotCollide() {
    // The path prefix is what keeps them apart — the case componentId's KDoc calls out.
    fun engineWithOneBlade() = Component(
      slot_key = SlotKeys.ENGINE,
      children = listOf(
        Component(
          slot_key = SlotKeys.PROPELLER,
          children = listOf(Component(slot_key = SlotKeys.BLADE)),
        ),
      ),
    )

    val thing = Thing(
      id = "t-2",
      components = listOf(
        Component(
          slot_key = SlotKeys.AIRFRAME,
          children = listOf(engineWithOneBlade(), engineWithOneBlade()),
        ),
      ),
    ).withDerivedComponentIds()

    val bladeIds = thing.allComponentsInSlot(SlotKeys.BLADE)
      .map { it.id }

    assertThat(bladeIds).containsExactly(
      "t-2:engine.0.propeller.0.blade.0",
      "t-2:engine.1.propeller.0.blade.0",
    )
    assertThat(bladeIds.toSet()).hasSize(2)
  }

  @Test
  fun derivationIsIdempotent() {
    val once = twin().withDerivedComponentIds()

    assertThat(once.withDerivedComponentIds()).isEqualTo(once)
  }

  @Test
  fun aThingWithNoIdYetProducesIdsItWillNotKeep() {
    // Ids embed the Thing id, which does not exist until save — hence re-derivation there.
    val unsaved =
      Thing(id = "", components = listOf(Component(slot_key = "airframe")))

    val derived = unsaved.withDerivedComponentIds()

    assertThat(derived.components.single().id).isEqualTo(":airframe.0")
  }
}
