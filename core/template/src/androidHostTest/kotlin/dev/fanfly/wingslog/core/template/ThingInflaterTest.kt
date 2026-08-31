package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.Engine
import dev.fanfly.wingslog.thing.Propeller
import dev.fanfly.wingslog.thing.PropellerBlade
import dev.fanfly.wingslog.thing.PropellerHub
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import org.junit.Test

/**
 * That the client inflates a Thing exactly as `thingPayloads.ts` does.
 *
 * **Why byte-identical and not merely equivalent.** The backfill (#718) repairs the Things this
 * inflater never reached. If the two disagree, whichever runs second rewrites the first one's work —
 * and because component ids are the join key logs, tasks and squawks use to point at a component,
 * a disagreement about ids silently repoints every one of them. Divergence here is not a cosmetic
 * difference; it is data loss with no error.
 *
 * The expected values below are transcribed from `thingPayloads.ts`, not derived from this
 * implementation, so a change on either side has to be reconciled deliberately.
 */
class ThingInflaterTest {

  private val airplane = AirplaneTemplate.TEMPLATE

  /** A twin, so the multi-engine label and the per-engine path disambiguation are both exercised. */
  private fun twin() = Thing(
    id = "thing-1",
    make = "Cessna",
    model = "310",
    serial = "SN-1",
    tail_number = "N123AB",
    engine = listOf(
      Engine(
        make = "Continental",
        model = "IO-470",
        serial = "E-1",
        propeller = Propeller(
          hub = PropellerHub(make = "McCauley", model = "HUB-1", serial = "H-1"),
          blades = listOf(
            PropellerBlade(make = "McCauley", model = "BL", serial = "B-1"),
            PropellerBlade(make = "McCauley", model = "BL", serial = "B-2"),
          ),
        ),
      ),
      Engine(make = "Continental", model = "IO-470", serial = "E-2"),
    ),
  )

  @Test
  fun componentIdMatchesTheBackendDerivation() {
    // `${thingId}:${path.join(".")}` — transcribed from thingPayloads.ts.
    assertThat(ThingInflater.componentId("thing-1", listOf("airframe", "0")))
      .isEqualTo("thing-1:airframe.0")
    assertThat(ThingInflater.componentId("thing-1", listOf("engine", "1", "propeller", "0", "blade", "2")))
      .isEqualTo("thing-1:engine.1.propeller.0.blade.2")
  }

  @Test
  fun specCarriesTheFourConventionalKeysInOrder() {
    val inflated = ThingInflater.inflate(twin(), airplane)

    assertThat(inflated.spec.map { it.key })
      .containsExactly("make", "model", "serial", "tail_number").inOrder()
    assertThat(inflated.spec.map { it.value_ })
      .containsExactly("Cessna", "310", "SN-1", "N123AB").inOrder()
  }

  @Test
  fun emptySpecValuesAreDroppedRatherThanStoredBlank() {
    // The backend filters on `value.length > 0`. A Thing with no tail number has no tail_number
    // spec, not one holding "" — and a blank entry would make the two sides disagree.
    val noTail = twin().copy(tail_number = "", serial = "")

    val inflated = ThingInflater.inflate(noTail, airplane)

    assertThat(inflated.spec.map { it.key }).containsExactly("make", "model").inOrder()
  }

  @Test
  fun theComponentTreeMatchesTheBackendShape() {
    val inflated = ThingInflater.inflate(twin(), airplane)

    val airframe = inflated.components.single()
    assertThat(airframe.id).isEqualTo("thing-1:airframe.0")
    assertThat(airframe.slot_key).isEqualTo("airframe")
    assertThat(airframe.label).isEqualTo("Airframe")
    assertThat(airframe.make).isEqualTo("Cessna")

    val engines = airframe.children
    assertThat(engines.map { it.id })
      .containsExactly("thing-1:engine.0", "thing-1:engine.1").inOrder()
    // Numbered because there is more than one; a single-engine aircraft says just "Engine".
    assertThat(engines.map { it.label }).containsExactly("Engine 1", "Engine 2").inOrder()

    val propeller = engines[0].children.single()
    assertThat(propeller.id).isEqualTo("thing-1:engine.0.propeller.0")
    assertThat(propeller.slot_key).isEqualTo("propeller")
    // The propeller itself carries no make/model/serial — the hub does.
    assertThat(propeller.make).isEmpty()

    assertThat(propeller.children.map { it.id }).containsExactly(
      "thing-1:engine.0.propeller.0.hub.0",
      "thing-1:engine.0.propeller.0.blade.0",
      "thing-1:engine.0.propeller.0.blade.1",
    ).inOrder()
    assertThat(propeller.children.map { it.label })
      .containsExactly("Hub", "Blade 1", "Blade 2").inOrder()

    // The second engine has no propeller, so no children at all.
    assertThat(engines[1].children).isEmpty()
  }

  @Test
  fun aSingleEngineIsLabelledWithoutANumber() {
    val single = twin().copy(engine = listOf(Engine(make = "Lycoming", model = "O-320")))

    val inflated = ThingInflater.inflate(single, airplane)

    assertThat(inflated.components.single().children.single().label).isEqualTo("Engine")
  }

  @Test
  fun theTreeShapeComesFromStructureNotFromFilledFields() {
    // A half-filled aircraft still gets its skeleton, so logs have something stable to hang off and
    // filling a field in later renumbers nothing.
    val blank = Thing(
      id = "thing-2",
      engine = listOf(Engine(propeller = Propeller(blades = listOf(PropellerBlade())))),
    )

    val inflated = ThingInflater.inflate(blank, airplane)

    val airframe = inflated.components.single()
    assertThat(airframe.id).isEqualTo("thing-2:airframe.0")
    assertThat(airframe.children.single().children.single().children.map { it.id })
      .containsExactly("thing-2:engine.0.propeller.0.blade.0")
    // No hub was present, so no hub component — absent is not the same as blank.
    assertThat(inflated.spec).isEmpty()
  }

  @Test
  fun aThingWithNoEnginesGetsAnAirframeAndNothingElse() {
    // Cardinality is 0-or-more at every level. An airframe with no engines is a real shape — a
    // glider, or an aircraft whose engine records were never filled in — and it must still produce
    // its airframe so logs have something to hang off.
    val engineless = Thing(id = "t-1", make = "Schweizer", model = "SGS 1-26")

    val inflated = ThingInflater.inflate(engineless, airplane)

    val airframe = inflated.components.single()
    assertThat(airframe.slot_key).isEqualTo("airframe")
    assertThat(airframe.children).isEmpty()
  }

  @Test
  fun aPropellerWithNoBladesStillGetsItsPropellerComponent() {
    // Same at the leaf: blades are 0-or-more, and a propeller with none recorded is not the same as
    // no propeller. Collapsing the two would lose the propeller's own make/model/serial.
    val noBlades = Thing(
      id = "t-2",
      engine = listOf(Engine(propeller = Propeller(hub = PropellerHub(serial = "H-1")))),
    )

    val inflated = ThingInflater.inflate(noBlades, airplane)

    val propeller = inflated.components.single().children.single().children.single()
    assertThat(propeller.slot_key).isEqualTo("propeller")
    assertThat(propeller.children.map { it.slot_key }).containsExactly("hub")
  }

  @Test
  fun engineCountIsPreservedExactly() {
    // The count the UI iterates comes from this tree now, so an off-by-one here is an engine that
    // vanishes from the dashboard.
    val twin = twin()

    val engines = ThingInflater.inflate(twin, airplane).components.single().children

    assertThat(engines).hasSize(twin.engine.size)
  }

  @Test
  fun nameFallsBackFromTailNumberToMakeAndModel() {
    assertThat(ThingInflater.inflate(twin(), airplane).name).isEqualTo("N123AB")

    val noTail = twin().copy(tail_number = "")
    assertThat(ThingInflater.inflate(noTail, airplane).name).isEqualTo("Cessna 310")

    val nothing = Thing(id = "t")
    assertThat(ThingInflater.inflate(nothing, airplane).name).isEmpty()
  }

  @Test
  fun anExistingNameIsNeverOverwritten() {
    // The user named it. Regenerating from make/model on every write would silently rename it back.
    val named = twin().copy(name = "The Old Girl")

    assertThat(ThingInflater.inflate(named, airplane).name).isEqualTo("The Old Girl")
  }

  @Test
  fun inflationIsIdempotent() {
    val once = ThingInflater.inflate(twin(), airplane)
    val twice = ThingInflater.inflate(once, airplane)

    assertThat(twice).isEqualTo(once)
  }

  @Test
  fun rebuildingPreservesIdsWhenTheStructureIsUnchanged() {
    // Every write rebuilds spec and the tree from fields 2-6. That is safe precisely because the
    // derivation is positional: editing a value must not renumber anything, or every log pointing
    // at a component is orphaned by an unrelated edit.
    val once = ThingInflater.inflate(twin(), airplane)
    val edited = once.copy(make = "Beechcraft")

    val reInflated = ThingInflater.inflate(edited, airplane)

    assertThat(reInflated.components.map { it.id }).isEqualTo(once.components.map { it.id })
    assertThat(reInflated.specValue("make")).isEqualTo("Beechcraft")
  }

  @Test
  fun editingAValueReachesTheSpec() {
    // The regression this guards, found while working out how to test part 2: gating spec on
    // "already has one" meant an edited make never reached it. Harmless while the UI read
    // fields 2-6; invisible data loss once the UI reads spec (#668 part 1).
    val saved = ThingInflater.inflate(twin(), airplane)

    val reSaved = ThingInflater.inflate(saved.copy(make = "Beechcraft"), airplane)

    assertThat(reSaved.specValue("make")).isEqualTo("Beechcraft")
  }

  @Test
  fun addingAnEngineReachesTheComponentTree() {
    // The same regression, structurally: the tree was frozen after the first inflation, so an
    // engine added in the form never appeared on a dashboard that reads components.
    val saved = ThingInflater.inflate(
      Thing(id = "t", make = "Cessna", engine = listOf(Engine(make = "Lycoming"))),
      airplane,
    )
    assertThat(saved.allComponentsInSlot(SlotKeys.ENGINE)).hasSize(1)

    val reSaved =
      ThingInflater.inflate(saved.copy(engine = saved.engine + Engine(make = "Continental")), airplane)

    assertThat(reSaved.allComponentsInSlot(SlotKeys.ENGINE)).hasSize(2)
  }

  @Test
  fun removingAnEngineReachesTheComponentTree() {
    val saved = ThingInflater.inflate(twin(), airplane)
    assertThat(saved.allComponentsInSlot(SlotKeys.ENGINE)).hasSize(2)

    val reSaved = ThingInflater.inflate(saved.copy(engine = saved.engine.take(1)), airplane)

    assertThat(reSaved.allComponentsInSlot(SlotKeys.ENGINE)).hasSize(1)
  }

  @Test
  fun dnaIsWrittenEvenWhenComponentsAlreadyExist() {
    // A Thing migrated by the cutover has components but no DNA — the cutover predates field 12.
    // Absent DNA resolves correctly, but writing it here is what shrinks that population.
    val migrated = ThingInflater.inflate(twin(), template = null)
    assertThat(migrated.template).isNull()

    val reInflated = ThingInflater.inflate(migrated, airplane)

    assertThat(reInflated.template).isEqualTo(airplane)
    assertThat(reInflated.components).isEqualTo(migrated.components)
  }

  @Test
  fun aNonAirplaneThingGetsNoLegacyTree() {
    // The guard that matters once the picker ships (#739). A car reaches inflate with empty
    // components and empty legacy fields; without the template check it would be handed a lone
    // "Airframe" component — and that would be *stored*. Component ids are a join key, so a wrong
    // tree is not recoverable the way an empty one is.
    val car = airplane.copy(id = "car", display_name = "Car")
    val thing = Thing(id = "car-1", name = "The Truck", template = car)

    val inflated = ThingInflater.inflate(thing, car)

    assertThat(inflated.components).isEmpty()
    assertThat(inflated.name).isEqualTo("The Truck")
  }

  @Test
  fun aTemplateFilledSpecIsNotOverwritten() {
    // A Thing created from a template has its spec filled by the create form before it reaches
    // here, and its components are still empty. Gating spec on the components signal would
    // overwrite those values with the empty derivation from fields 2-6.
    val car = airplane.copy(id = "car", display_name = "Car")
    val fromForm = Thing(
      id = "car-1",
      template = car,
      spec = listOf(Spec(key = "vin", value_ = "1HGCM82633A004352")),
    )

    val inflated = ThingInflater.inflate(fromForm, car)

    assertThat(inflated.spec.map { it.key }).containsExactly("vin")
    assertThat(inflated.spec.single().value_).isEqualTo("1HGCM82633A004352")
  }

  @Test
  fun existingDnaIsNeverReplaced() {
    // A Thing created from another template must not have its DNA overwritten by the fallback.
    val custom = airplane.copy(id = "car", display_name = "Car")
    val thing = ThingInflater.inflate(twin().copy(template = custom), airplane)

    assertThat(thing.template?.id).isEqualTo("car")
  }
}
