package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.Component
import dev.fanfly.wingslog.thing.Spec
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
    // steering and rigging. Its repeating categories yield none, for the same reason. There is no
    // hull row: the hull IS the boat, so its make, model and number are the boat's spec fields.
    val rows = CanonicalTemplates.BOAT.componentRows(Thing(id = "t"))
    assertThat(rows.map { it.slot.slot_key })
      .containsExactly("steering", "rigging")
      .inOrder()
    assertThat(rows.map { it.component }).containsExactly(null, null)
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
    // One engine, so no index on it; two blades, so they take one.
    assertThat(rows.map { it.label }).containsExactly(
      "Engine", "Propeller", "Blade 1", "Blade 2",
    )
      .inOrder()
    assertThat(rows.map { it.depth }).containsExactly(0, 1, 2, 2)
      .inOrder()
    // The path is what every edit action addresses, so it has to survive the walk intact.
    assertThat(rows.last().path).containsExactly(
      SlotKeys.ENGINE to 0,
      SlotKeys.PROPELLER to 0,
      SlotKeys.BLADE to 1,
    )
      .inOrder()
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

    val labels = bike.componentRows(thing)
      .map { it.label }

    assertThat(labels).containsExactly("Drivetrain", "Wheel 1", "Wheel 2")
  }

  @Test
  fun aBikeCannotBeGivenAnEngine() {
    // The reported bug, stated as a rule: what may be added comes from the template's slots, so a
    // preset that never declares an engine can never be offered one.
    val addable = bike.addableSlotsUnder(emptyList(), bike.componentRows(Thing(id = "t")))
      .map { it.slot_key }

    assertThat(addable).doesNotContain("engine")
    assertThat(addable).containsExactly("wheel")
  }

  @Test
  fun aBikeIsOfferedNoThirdWheel() {
    // A bike has a front and a rear, and there is no third. Uncapped, the form offered to add a
    // fifth wheel — a question with no right answer. `repeatable` still makes them optional;
    // `max_instances` makes them finite.
    fun addableWith(wheels: Int): List<String> {
      val thing = Thing(id = "t", components = List(wheels) { Component(slot_key = "wheel") })
      return bike.addableSlotsUnder(emptyList(), bike.componentRows(thing))
        .map { it.slot_key }
    }

    assertThat(addableWith(wheels = 0)).containsExactly("wheel")
    assertThat(addableWith(wheels = 1)).containsExactly("wheel")
    assertThat(addableWith(wheels = 2)).isEmpty()
  }

  @Test
  fun neitherAVehicleNorABikeTracksBrakesAsAPart() {
    // Brake pads and rotors are a SERVICE — a task against distance, and a record when it is done.
    // Both presets declared a brakes slot, both sat empty on every Thing, and both are gone.
    assertThat(automotive.component_slots.map { it.slot_key }).doesNotContain("brakes")
    assertThat(bike.component_slots.map { it.slot_key }).doesNotContain("brakes")
    assertThat(bike.component_slots.map { it.slot_key }).containsExactly("drivetrain", "wheel")
      .inOrder()
  }

  // --- A slot's own declared fields, beyond make/model/serial ---

  @Test
  fun aWheelAsksForItsPositionAndPressureAsWellAsItsMakeAndModel() {
    // The make/model/serial triple is a floor, not the whole vocabulary: what tells one tyre from
    // the other three is where it sits and what it runs at, and neither is any of the three.
    val tire = automotive.component_slots.single { it.slot_key == "tire" }
    assertThat(tire.spec_fields.map { it.key }).containsExactly("position", "psi").inOrder()
    assertThat(tire.spec_keys).containsExactly("make", "model")

    // The vocabulary is the template's, because it is domain knowledge. A car has four corners
    // and a spare; a bike has a front and a rear and nothing else.
    val position = tire.spec_fields.single { it.key == "position" }
    assertThat(position.options).containsAtLeast("Front Left", "Rear Right", "Spare")
    assertThat(
      bike.component_slots.single { it.slot_key == "wheel" }
        .spec_fields.single { it.key == "position" }.options,
    ).containsExactly("Front", "Rear").inOrder()

    // Free text would let the same wheel be "RR", "rear right" and "Rear-Right" across three cars.
    assertThat(position.options).isNotEmpty()
    assertThat(tire.spec_fields.single { it.key == "psi" }.numeric).isTrue()
  }

  @Test
  fun aPositionNamesTheWheelInsteadOfAnOrdinal() {
    // "Tire 3" is a number this code invents from storage order and means nothing on the car.
    // A position is where the owner will actually look for it, so it takes the chip's label.
    val car = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = "tire",
          make = "Michelin",
          model = "Pilot Sport",
          spec = listOf(Spec(key = "position", value_ = "Front Left"), Spec(key = "psi", value_ = "32")),
        ),
        // The second records nothing but a make, which is the common case: every declared field
        // is optional, so the ordinal has to still be there to fall back on.
        Component(slot_key = "tire", make = "Michelin"),
      ),
    )
    val chips = automotive.componentRows(car)
      .filter { it.slot.slot_key == "tire" }
      .mapNotNull { it.chipLines }

    assertThat(chips[0].label).isEqualTo("Front Left")
    assertThat(chips[0].headline).isEqualTo("Michelin Pilot Sport")
    // The position is NOT repeated as a line — it is already the label.
    assertThat(chips[0].specs.map { it.label to it.value })
      .containsExactly("Normal PSI" to "32")

    assertThat(chips[1].label).isEqualTo("Tire 2")
    // Nothing recorded means nothing drawn: "Normal PSI" over a blank reads as a failed load.
    assertThat(chips[1].specs).isEmpty()
  }

  @Test
  fun anAutomotiveEngineIsOptionalButCappedAtOne() {
    // Optional is `repeatable`, which is what lets an EV hold none. Singular is `max_instances`,
    // which is what the schema could not say before: a bool can only mean "exactly one" — an
    // unremovable empty Engine block on an EV — or "any number", offering a hatchback a second.
    val empty = Thing(id = "t")
    assertThat(
      automotive.addableSlotsUnder(emptyList(), automotive.componentRows(empty))
        .map { it.slot_key })
      .containsExactly("engine", "tire")
      .inOrder()

    val withEngine = Thing(
      id = "t",
      components = listOf(Component(slot_key = "engine", make = "Honda")),
    )
    assertThat(
      automotive.addableSlotsUnder(emptyList(), automotive.componentRows(withEngine))
        .map { it.slot_key })
      .containsExactly("tire")

    // Tyres are uncapped: four on a car, six on a pickup, and the template does not guess.
    val fourTyres = Thing(
      id = "t",
      components = List(4) { Component(slot_key = "tire") },
    )
    assertThat(
      automotive.addableSlotsUnder(emptyList(), automotive.componentRows(fourTyres))
        .map { it.slot_key })
      .contains("tire")
  }

  @Test
  fun aCarTracksItsEngineAndItsTyresAndNothingElse() {
    // Nobody logs the make and serial of their car battery, and nobody logs brakes as parts — they
    // log the SERVICE, which is a task and a record. Both slots existed, both sat empty, and
    // together they made a car declare more trackable parts than an aeroplane has.
    assertThat(automotive.component_slots.map { it.slot_key })
      .containsExactly("engine", "tire")
      .inOrder()
  }

  @Test
  fun addableSlotsAreScopedToTheirParent() {
    // A blade belongs under a propeller, not at the root. Offering it anywhere else would build a
    // tree the template cannot describe.
    assertThat(
      airplane.addableSlotsUnder(emptyList())
        .map { it.slot_key })
      .containsExactly("engine")
    assertThat(
      airplane.addableSlotsUnder(
        listOf(
          SlotKeys.ENGINE to 0,
          SlotKeys.PROPELLER to 0
        )
      )
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
  fun theTemplateSaysWhichPartsAreASetRatherThanTheDashboardGuessingFromDepth() {
    // Nesting used to decide this: a part inside another was a set, a top-level one an individual.
    // That put a car's four tyres on four full-width rows while an aeroplane's four blades shared
    // two — so a car appeared to track more than an aeroplane, which is backwards. The slot says
    // so now, wherever it sits.
    val boat = Thing(
      id = "t",
      components = listOf(Component(slot_key = "propulsion", make = "Yanmar")),
    )
    assertThat(
      CanonicalTemplates.BOAT.componentRows(boat)
        .single { it.slot.slot_key == "propulsion" }.rendersAsChip,
    ).isTrue()

    val car = Thing(
      id = "t",
      components = listOf(
        Component(slot_key = "engine", make = "Honda"),
        Component(slot_key = "tire", make = "Michelin"),
      ),
    )
    val byKey = automotive.componentRows(car).associateBy { it.slot.slot_key }
    assertThat(byKey.getValue("tire").rendersAsChip).isTrue()
    // The engine is the individual it always was: one part with a history, and the card is where
    // that history hangs. Compacting everything would have been the opposite mistake.
    assertThat(byKey.getValue("engine").rendersAsChip).isFalse()

    // The nested case the old rule was written for is unchanged.
    val plane = Thing(
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
    assertThat(
      airplane.componentRows(plane)
        .single { it.slot.slot_key == SlotKeys.BLADE }.rendersAsChip,
    ).isTrue()
  }

  @Test
  fun aChipShowsTheMakeAndModelACardWouldAndFallsBackToTheSerialWhenThereIsNone() {
    // Compacting must not cost information — that was the objection to chips outside the blade
    // case, and the reason the chip carries all three lines rather than a serial alone.
    val boat = Thing(
      id = "t",
      components = listOf(
        Component(slot_key = "propulsion", make = "Yamaha", model = "F150", serial = "6CE1001"),
        Component(slot_key = "propulsion", make = "Yamaha", model = "F150", serial = "6CE1002"),
      ),
    )
    val chips = CanonicalTemplates.BOAT.componentRows(boat)
      .filter { it.slot.slot_key == "propulsion" }
      .mapNotNull { it.chipLines }

    assertThat(chips.map { it.label }).containsExactly("Propulsion 1", "Propulsion 2").inOrder()
    assertThat(chips.map { it.headline }).containsExactly("Yamaha F150", "Yamaha F150")
    assertThat(chips.map { it.serial }).containsExactly("6CE1001", "6CE1002").inOrder()

    // A blade declares `spec_keys: "serial"`, so it has no make or model to head the chip and its
    // serial takes that line rather than sitting under a blank one.
    val plane = Thing(
      id = "t",
      components = listOf(
        Component(
          slot_key = SlotKeys.ENGINE,
          children = listOf(
            Component(
              slot_key = SlotKeys.PROPELLER,
              children = listOf(
                // Make is stored but not declared: the chip must read the SLOT, not the record.
                Component(slot_key = SlotKeys.BLADE, make = "Hartzell", serial = "J4471"),
              ),
            ),
          ),
        ),
      ),
    )
    val blade = airplane.componentRows(plane)
      .single { it.slot.slot_key == SlotKeys.BLADE }.chipLines!!
    assertThat(blade.headline).isEqualTo("J4471")
    assertThat(blade.serial).isEmpty()
  }

  @Test
  fun chipsOfOneSlotMergeIntoOneBlockWithoutReorderingTheTemplate() {
    // Collecting every chip slot and drawing it first would be simpler and wrong: it would hoist a
    // set above the individual the template declared before it. The boat is the case that shows
    // it — steering and rigging are cards declared AFTER two chipped sets.
    val boat = Thing(
      id = "t",
      components = listOf(
        Component(slot_key = "propulsion", make = "Yamaha"),
        Component(slot_key = "propulsion", make = "Yamaha"),
        Component(slot_key = "steering", make = "SeaStar"),
      ),
    )
    val groups = CanonicalTemplates.BOAT.componentTree(boat)
      .filter { it.row.component != null }
      .componentGroups()

    assertThat(groups).hasSize(2)
    // Both engines in ONE block, not two rows — the whole point.
    assertThat((groups[0] as ComponentGroup.Chips).nodes).hasSize(2)
    // And steering stays after them, where the template put it.
    assertThat((groups[1] as ComponentGroup.Card).node.row.slot.slot_key).isEqualTo("steering")
  }

  @Test
  fun tyresAreLaidOutByPositionWithTheUnplacedOnesTrailing() {
    // Two to a row on a phone, so ordering by the template's own list makes the block read as the
    // car: front left beside front right, rear left beside rear right. Typed order would scatter
    // them. A wheel with no position cannot claim a corner, so it goes after the ones that can.
    fun tire(position: String?, make: String) = Component(
      slot_key = "tire",
      make = make,
      spec = position?.let { listOf(Spec(key = "position", value_ = it)) }.orEmpty(),
    )

    val car = Thing(
      id = "t",
      components = listOf(
        tire("Spare", "E"),
        tire(null, "F"),
        tire("Rear Right", "D"),
        tire("Front Left", "A"),
        tire("Rear Left", "C"),
        tire("Front Right", "B"),
      ),
    )
    val chips = (
      automotive.componentTree(car)
        .filter { it.row.component != null }
        .componentGroups()
        .single() as ComponentGroup.Chips
      ).nodes

    assertThat(chips.map { it.row.component?.make })
      .containsExactly("A", "B", "C", "D", "E", "F")
      .inOrder()
  }

  @Test
  fun everyTyreOfACarLandsInOneBlock() {
    val car = Thing(
      id = "t",
      components = listOf(Component(slot_key = "engine", make = "Honda")) +
        List(4) { Component(slot_key = "tire", make = "Michelin") },
    )
    val groups = automotive.componentTree(car)
      .filter { it.row.component != null }
      .componentGroups()

    assertThat(groups).hasSize(2)
    assertThat((groups[0] as ComponentGroup.Card).node.row.slot.slot_key).isEqualTo("engine")
    assertThat((groups[1] as ComponentGroup.Chips).nodes).hasSize(4)
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

    assertThat(
      airplane.componentsMissingSerials(thing)
        .map { it.label })
      .containsExactly("Engine")
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
    // A tyre has no serial anyone reads off it, so leaving it blank is complete, not invalid.
    val thing = Thing(
      id = "t",
      components = listOf(Component(slot_key = "tire", serial = "")),
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
    )
      .single { it.slot.slot_key == SlotKeys.BLADE }

    assertThat(blade.fields).containsExactly(ComponentField.SERIAL)
  }

  @Test
  fun aSlotThatDeclaresNoFieldsAsksForAllThree() {
    // The default keeps every preset that has not thought about it unchanged.
    val engine = airplane.componentRows(
      Thing(
        id = "t",
        components = listOf(Component(slot_key = SlotKeys.ENGINE))
      ),
    )
      .single { it.slot.slot_key == SlotKeys.ENGINE }

    assertThat(engine.fields).containsExactly(
      ComponentField.MAKE,
      ComponentField.MODEL,
      ComponentField.SERIAL,
    )
      .inOrder()
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

    val engine = airplane.componentTree(thing)
      .single()

    assertThat(engine.row.label).isEqualTo("Engine")
    // The propeller flows inside the engine's card rather than nesting into one of its own.
    val propeller = engine.inlineGroups.single()
      .single()
    assertThat(propeller.row.label).isEqualTo("Propeller")
    // Blades hang off the propeller as chips, not as cards beside it.
    assertThat(propeller.cardChildren).isEmpty()
    assertThat(propeller.chipChildren.map { it.row.label })
      .containsExactly("Blade 1", "Blade 2")
      .inOrder()
  }

  // --- How a slot asks to be laid out ---

  @Test
  fun aPropellerFlowsIntoItsEnginesCardRatherThanNestingIntoOne() {
    // A propeller is part of how an owner describes the engine, not somewhere to navigate into.
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

    val engine = airplane.componentTree(thing)
      .single()

    assertThat(engine.cardChildren).isEmpty()
    val propeller = engine.inlineGroups.single()
      .single()
    assertThat(propeller.row.slot.slot_key).isEqualTo(SlotKeys.PROPELLER)
    // Its blades hang off it as chips, still inside the same card.
    assertThat(propeller.chipChildren.map { it.row.label }).containsExactly("Blade")
  }

  @Test
  fun compactFieldsPutEverythingOnItsOwnLineExceptTheLastTwo() {
    // Make alone, then model beside serial — the shape a plate reads in.
    val engine = airplane.componentRows(
      Thing(
        id = "t",
        components = listOf(Component(slot_key = SlotKeys.ENGINE))
      ),
    )
      .single { it.slot.slot_key == SlotKeys.ENGINE }

    assertThat(engine.slot.compact_fields).isTrue()
    assertThat(engine.leadingFields).containsExactly(ComponentField.MAKE)
    assertThat(engine.pairedFields)
      .containsExactly(ComponentField.MODEL, ComponentField.SERIAL)
      .inOrder()
  }

  @Test
  fun aSerialOnlySlotHasNothingToPairAndPacksItsInstancesInstead() {
    val blade = airplane.slot(SlotKeys.BLADE)!!
    val row = airplane.componentRows(
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
    )
      .single { it.slot.slot_key == SlotKeys.BLADE }

    assertThat(blade.compact_fields).isTrue()
    assertThat(row.leadingFields).isEmpty()
    assertThat(row.pairedFields).containsExactly(ComponentField.SERIAL)
  }

  @Test
  fun theSpecBlockAsksInTheOrderTheFormAlwaysHas() {
    // make, model, then serial and tail number sharing a line.
    assertThat(airplane.spec_fields.map { it.key })
      .containsExactly("make", "model", "serial", "tail_number")
      .inOrder()
    assertThat(airplane.spec_fields.filter { it.compact }
                 .map { it.key })
      .containsExactly("serial", "tail_number")
      .inOrder()
  }

  @Test
  fun asingleComponentDropsTheIndexFromItsLabel() {
    // "Engine", not "Engine 1". A number only earns its place once there is something to tell it
    // apart from, which is how the form has always read.
    val one = Thing(
      id = "t",
      components = listOf(Component(slot_key = SlotKeys.ENGINE)),
    )
    assertThat(
      airplane.componentRows(one)
        .single { it.slot.slot_key == SlotKeys.ENGINE }.label,
    ).isEqualTo("Engine")

    val two = Thing(
      id = "t",
      components = listOf(
        Component(slot_key = SlotKeys.ENGINE),
        Component(slot_key = SlotKeys.ENGINE),
      ),
    )
    assertThat(
      airplane.componentRows(two)
        .filter { it.slot.slot_key == SlotKeys.ENGINE }
        .map { it.label },
    ).containsExactly("Engine 1", "Engine 2")
      .inOrder()
  }

  @Test
  fun bladesReachTheEditFormEvenThoughTheDashboardDrawsThemAsChips() {
    // They once reached neither list: filtered out of the inline groups for being chips, and out
    // of cardChildren for being inline. The edit form rendered no blades at all.
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

    val propeller = airplane.componentTree(thing)
      .single().inlineGroups.single()
      .single()

    assertThat(
      propeller.inlineGroups.single()
        .map { it.row.label })
      .containsExactly("Blade 1", "Blade 2")
      .inOrder()
    // The dashboard still splits them out as chips rather than blocks.
    assertThat(propeller.inlineBlockGroups).isEmpty()
    assertThat(propeller.chipChildren).hasSize(2)
  }

  @Test
  fun aThingWithNoComponentsCanStillBeGivenOne() {
    // Removing the last engine emptied the tree, and the form treated an empty tree as nothing to
    // draw — taking the Add control with it and leaving no way back. What may be added comes from
    // the template, so it survives the components being gone.
    val empty = Thing(id = "t")

    assertThat(airplane.componentRows(empty)).isEmpty()
    assertThat(
      airplane.addableSlotsUnder(emptyList())
        .map { it.slot_key })
      .containsExactly("engine")
  }

  @Test
  fun aTemplateWithNothingToAddHasNothingToDraw() {
    // The other half of the same rule: home and custom declare no slots at all, so the section is
    // genuinely empty rather than empty-with-a-button.
    listOf(
      CanonicalTemplates.HOME,
      CanonicalTemplates.CUSTOM
    ).forEach { template ->
      assertThat(template.componentRows(Thing(id = "t"))).isEmpty()
      assertThat(template.addableSlotsUnder(emptyList())).isEmpty()
    }
  }
}
