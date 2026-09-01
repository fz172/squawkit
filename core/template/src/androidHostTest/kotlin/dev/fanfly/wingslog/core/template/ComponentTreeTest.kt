package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * The component tree, walked from the template rather than from airplane knowledge (#729).
 *
 * The bug this replaces: a bike offered to add an engine, and the engine it added contained a
 * propeller with numbered blades — because the widgets were airframe/engine/propeller/blade
 * composables that every template got.
 */
class ComponentTreeTest {

  private val airplane = AirplaneTemplate.TEMPLATE
  private val bike = CanonicalTemplates.BIKE
  private val automotive = CanonicalTemplates.AUTOMOTIVE
  private val custom = CanonicalTemplates.CUSTOM

  @Test
  fun anEmptyThingStillOffersItsFixedSlotsToFillIn() {
    // A non-repeatable slot yields a row with no component, which is what makes a blank form
    // typeable. Without it the user is shown nothing and cannot start.
    val rows = airplane.componentRows(Thing(id = "t"))

    assertThat(rows.map { it.slot.slot_key }).containsExactly("airframe")
    assertThat(rows.single().component).isNull()
    assertThat(rows.single().path).containsExactly(SlotKeys.AIRFRAME to 0)
  }

  @Test
  fun storedComponentsAreWalkedDepthFirstWithTheirPaths() {
    val thing = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = SlotKeys.AIRFRAME,
          children = listOf(
            Component(
              slot_key = SlotKeys.ENGINE,
              children = listOf(
                Component(
                  slot_key = SlotKeys.PROPELLER,
                  children = listOf(
                    Component(slot_key = SlotKeys.HUB),
                    Component(slot_key = SlotKeys.BLADE),
                    Component(slot_key = SlotKeys.BLADE),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    )

    val rows = airplane.componentRows(thing)

    assertThat(rows.map { it.label }).containsExactly(
      "Airframe", "Engine 1", "Propeller", "Hub", "Blade 1", "Blade 2",
    ).inOrder()
    assertThat(rows.map { it.depth }).containsExactly(0, 1, 2, 3, 3, 3).inOrder()
    // The path is what every edit action addresses, so it has to survive the walk intact.
    assertThat(rows.last().path).containsExactly(
      SlotKeys.AIRFRAME to 0,
      SlotKeys.ENGINE to 0,
      SlotKeys.PROPELLER to 0,
      SlotKeys.BLADE to 1,
    ).inOrder()
  }

  @Test
  fun onlyRepeatableSlotsAreNumbered() {
    // "Airframe 1" reads as though a second is coming. A car with one engine should not say
    // "Engine 1" either — but a bike's two wheels must be told apart.
    val thing = Thing(
      id = "t",
      components = listOf(
        Component(slot_key = "drivetrain"),
        Component(slot_key = "wheel"),
        Component(slot_key = "wheel"),
      ),
    )

    val labels = bike.componentRows(thing).map { it.label }

    assertThat(labels).containsExactly("Drivetrain", "Wheel 1", "Wheel 2")
  }

  @Test
  fun aBikeCannotBeGivenAnEngine() {
    // The reported bug, stated as a rule: what may be added comes from the template's slots, so a
    // preset that never declares an engine can never be offered one.
    val addable = bike.addableSlotsUnder(emptyList()).map { it.slot_key }

    assertThat(addable).doesNotContain("engine")
    assertThat(addable).containsExactly("brakes", "wheel")
  }

  @Test
  fun anAutomotiveEngineIsAddableBecauseItsSlotRepeats() {
    // Optional is expressed as repeatable — the only cardinality the schema has — so an EV can
    // hold none and a twin can hold two.
    assertThat(automotive.addableSlotsUnder(emptyList()).map { it.slot_key })
      .containsExactly("engine", "brakes", "tire")
  }

  @Test
  fun addableSlotsAreScopedToTheirParent() {
    // A blade belongs under a propeller, not at the root. Offering it anywhere else would build a
    // tree the template cannot describe.
    assertThat(airplane.addableSlotsUnder(emptyList()).map { it.slot_key }).isEmpty()
    assertThat(
      airplane.addableSlotsUnder(listOf(SlotKeys.AIRFRAME to 0)).map { it.slot_key },
    ).containsExactly("engine")
    assertThat(
      airplane.addableSlotsUnder(
        listOf(SlotKeys.AIRFRAME to 0, SlotKeys.ENGINE to 0, SlotKeys.PROPELLER to 0),
      ).map { it.slot_key },
    ).containsExactly("blade")
  }

  @Test
  fun aNewComponentBringsItsFixedChildrenButNotItsRepeatableOnes() {
    // Adding an engine should produce its propeller and hub, because the template says those always
    // exist — but not a blade, whose count the template deliberately leaves to the user.
    val engineSlot = airplane.slot(SlotKeys.ENGINE)!!

    val engine = newComponentFor(engineSlot)

    assertThat(engine.slot_key).isEqualTo(SlotKeys.ENGINE)
    val propeller = engine.children.single()
    assertThat(propeller.slot_key).isEqualTo(SlotKeys.PROPELLER)
    assertThat(propeller.children.map { it.slot_key }).containsExactly(SlotKeys.HUB)
  }

  @Test
  fun aTemplateWithNoSlotsRendersNoRows() {
    // custom declares nothing, so the empty tree is a real state rather than a defensive branch.
    assertThat(custom.componentRows(Thing(id = "t"))).isEmpty()
    assertThat(custom.addableSlotsUnder(emptyList())).isEmpty()
    assertThat(CanonicalTemplates.HOME.componentRows(Thing(id = "t"))).isEmpty()
  }

  // --- Serials are enforced where the template expects one and a component exists ---

  @Test
  fun aPresentComponentMissingAnExpectedSerialIsReported() {
    // The engine, not the airframe: the airframe *is* the thing, so its serial is the Thing's and
    // lives in spec. Its slot expects none (#729).
    val thing = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = SlotKeys.AIRFRAME,
          children = listOf(Component(slot_key = SlotKeys.ENGINE, serial = "")),
        ),
      ),
    )

    assertThat(airplane.componentsMissingSerials(thing).map { it.label })
      .containsExactly("Engine 1")
  }

  @Test
  fun theAirframeExpectsNoSerialOfItsOwn() {
    // Identity lives in spec and nowhere else. A blank airframe serial is not a validation failure
    // because the airframe has no serial to give — the Thing's `serial` spec field holds it.
    val thing = Thing(
      id = "t",
      components = listOf(Component(slot_key = SlotKeys.AIRFRAME, serial = "")),
    )

    assertThat(airplane.slot(SlotKeys.AIRFRAME)?.serial_expected).isFalse()
    assertThat(airplane.componentsMissingSerials(thing)).isEmpty()
  }

  @Test
  fun anAbsentComponentIsNotMissingASerial() {
    // A car with no engine recorded is complete, not invalid. Validating declared-but-absent slots
    // would block saving a form the user has legitimately left empty.
    assertThat(automotive.componentsMissingSerials(Thing(id = "t"))).isEmpty()
  }

  @Test
  fun aSlotThatExpectsNoSerialIsNeverReported() {
    val thing = Thing(
      id = "t",
      components = listOf(Component(slot_key = "battery", serial = "")),
    )

    assertThat(automotive.componentsMissingSerials(thing)).isEmpty()
  }
}
