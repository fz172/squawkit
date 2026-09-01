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
    // Engine repeats, so an empty airplane offers no rows at all — "how many" is the user's to
    // decide and the add control belongs to the parent. A preset with a fixed slot does yield one.
    assertThat(airplane.componentRows(Thing(id = "t"))).isEmpty()

    // A preset with fixed slots yields a row each, with no component behind them yet — the boat's
    // hull, steering and rigging. Its repeating categories yield none, for the same reason.
    val rows = CanonicalTemplates.BOAT.componentRows(Thing(id = "t"))
    assertThat(rows.map { it.slot.slot_key })
      .containsExactly("hull", "steering", "rigging").inOrder()
    assertThat(rows.map { it.component }).containsExactly(null, null, null)
  }

  @Test
  fun storedComponentsAreWalkedDepthFirstWithTheirPaths() {
    val thing = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = SlotKeys.ENGINE,
          children = listOf(
            Component(
              slot_key = SlotKeys.PROPELLER,
              children = listOf(
                Component(slot_key = SlotKeys.BLADE),
                Component(slot_key = SlotKeys.BLADE),
              ),
            ),
          ),
        ),
      ),
    )

    val rows = airplane.componentRows(thing)

    // Engine at the root, propeller on the engine, blades on the propeller — how the parts
    // actually attach. No airframe row repeating the thing's identity, and no separate hub.
    assertThat(rows.map { it.label }).containsExactly(
      "Engine 1", "Propeller", "Blade 1", "Blade 2",
    ).inOrder()
    assertThat(rows.map { it.depth }).containsExactly(0, 1, 2, 2).inOrder()
    // The path is what every edit action addresses, so it has to survive the walk intact.
    assertThat(rows.last().path).containsExactly(
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
    assertThat(airplane.addableSlotsUnder(emptyList()).map { it.slot_key })
      .containsExactly("engine")
    assertThat(
      airplane.addableSlotsUnder(listOf(SlotKeys.ENGINE to 0, SlotKeys.PROPELLER to 0))
        .map { it.slot_key },
    ).containsExactly("blade")
  }

  @Test
  fun aNewComponentBringsItsFixedChildrenButNotItsRepeatableOnes() {
    // Adding an engine should produce its propeller and hub, because the template says those always
    // exist — but not a blade, whose count the template deliberately leaves to the user.
    val engineSlot = airplane.slot(SlotKeys.ENGINE)!!

    val engine = newComponentFor(engineSlot)

    assertThat(engine.slot_key).isEqualTo(SlotKeys.ENGINE)
    // Its propeller, because a propeller always exists on an engine — but no blade, whose count
    // the template deliberately leaves to the user.
    val propeller = engine.children.single()
    assertThat(propeller.slot_key).isEqualTo(SlotKeys.PROPELLER)
    assertThat(propeller.children).isEmpty()
  }

  @Test
  fun aTopLevelRepeatingPartStaysACardWhileANestedOneBecomesAChip() {
    // The boat groups by function, so a propulsion item is a component in its own right — its make
    // and model are worth reading, and a chip would show only the serial. A blade inside a
    // propeller is the opposite case.
    val boat = Thing(
      id = "t",
      components = listOf(Component(slot_key = "propulsion", make = "Yanmar")),
    )

    assertThat(
      CanonicalTemplates.BOAT.componentRows(boat)
        .single { it.slot.slot_key == "propulsion" }.rendersAsChip,
    ).isFalse()
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
    val thing = Thing(
      id = "t",
      components = listOf(Component(slot_key = SlotKeys.ENGINE, serial = "")),
    )

    assertThat(airplane.componentsMissingSerials(thing).map { it.label })
      .containsExactly("Engine 1")
  }

  @Test
  fun theAirplaneDeclaresNoAirframeOrHubSlot() {
    // The airframe *is* the thing — a row for it repeated the identity block verbatim — and a
    // propeller's make, model and serial ARE the hub's, so asking for both asked twice (#729).
    assertThat(airplane.slot(SlotKeys.LEGACY_AIRFRAME)).isNull()
    assertThat(airplane.slot(SlotKeys.LEGACY_HUB)).isNull()
    assertThat(airplane.component_slots.map { it.slot_key }).containsExactly("engine")
    assertThat(airplane.slot(SlotKeys.PROPELLER)?.serial_expected).isTrue()
  }

  @Test
  fun aRepeatingLeafSlotRendersAsChips() {
    // Blades, wheels, tyres, rudders — near-identical parts told apart by a serial. A card each
    // buries the tree in scroll and says nothing a chip does not.
    val thing = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = SlotKeys.ENGINE,
          children = listOf(
            Component(
              slot_key = SlotKeys.PROPELLER,
              children = listOf(Component(slot_key = SlotKeys.BLADE)),
            ),
          ),
        ),
      ),
    )

    val rows = airplane.componentRows(thing)

    assertThat(rows.single { it.slot.slot_key == SlotKeys.BLADE }.rendersAsChip).isTrue()
    // A propeller repeats nothing and has children of its own, so it stays a card.
    assertThat(rows.single { it.slot.slot_key == SlotKeys.PROPELLER }.rendersAsChip)
      .isFalse()
    assertThat(rows.single { it.slot.slot_key == SlotKeys.ENGINE }.rendersAsChip).isFalse()
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

  // --- Which fields a slot asks for, and how the tree nests ---

  @Test
  fun aBladeAsksForItsSerialAndNothingElse() {
    // Blades are a matched set: their make and model are the propeller's, and asking per blade
    // invites a mix that cannot exist on a real aeroplane (PRD §4.3's spec_keys).
    val blade = airplane.componentRows(
      Thing(
        id = "t",
        components = listOf(
          Component(
            slot_key = SlotKeys.ENGINE,
            children = listOf(
              Component(
                slot_key = SlotKeys.PROPELLER,
                children = listOf(Component(slot_key = SlotKeys.BLADE)),
              ),
            ),
          ),
        ),
      ),
    ).single { it.slot.slot_key == SlotKeys.BLADE }

    assertThat(blade.fields).containsExactly(ComponentField.SERIAL)
  }

  @Test
  fun aSlotThatDeclaresNoFieldsAsksForAllThree() {
    // The default keeps every preset that has not thought about it unchanged.
    val engine = airplane.componentRows(
      Thing(id = "t", components = listOf(Component(slot_key = SlotKeys.ENGINE))),
    ).single { it.slot.slot_key == SlotKeys.ENGINE }

    assertThat(engine.fields).containsExactly(
      ComponentField.MAKE,
      ComponentField.MODEL,
      ComponentField.SERIAL,
    ).inOrder()
  }

  @Test
  fun theTreeNestsChildrenUnderTheirParent() {
    // What draws containment: an engine holding its propeller, holding its blades. The flat walk
    // still exists for validation and ordering, but nothing renders from it any more.
    val thing = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = SlotKeys.ENGINE,
          children = listOf(
            Component(
              slot_key = SlotKeys.PROPELLER,
              children = listOf(
                Component(slot_key = SlotKeys.BLADE),
                Component(slot_key = SlotKeys.BLADE),
              ),
            ),
          ),
        ),
      ),
    )

    val engine = airplane.componentTree(thing).single()

    assertThat(engine.row.label).isEqualTo("Engine 1")
    val propeller = engine.cardChildren.single()
    assertThat(propeller.row.label).isEqualTo("Propeller")
    // Blades hang off the propeller as chips, not as cards beside it.
    assertThat(propeller.cardChildren).isEmpty()
    assertThat(propeller.chipChildren.map { it.row.label })
      .containsExactly("Blade 1", "Blade 2").inOrder()
  }
}
