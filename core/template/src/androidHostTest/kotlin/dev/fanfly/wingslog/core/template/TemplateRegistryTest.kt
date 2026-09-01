package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.Noun
import dev.fanfly.wingslog.thing.Section
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * Registry resolution, and the airplane preset's invariants.
 *
 * The assertions worth reading are the ones about *absent* DNA and about capability completeness —
 * both are properties Phase 2's later tasks lean on without re-checking.
 */
class TemplateRegistryTest {

  private val registry =
    BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE)

  @Test
  fun aThingCarryingDnaResolvesToItsOwnTemplate() {
    // The ordinary path once the picker exists: the template is a field read, not a lookup.
    val custom = ThingTemplate(
      id = "airplane",
      version = 7,
      lexicon = Lexicon(
        thing = Noun(
          singular = "glider",
          plural = "gliders",
          article = "a"
        )
      ),
    )

    val resolved =
      registry.forThingWithFallback(Thing(id = "t1", template = custom))

    assertThat(resolved).isEqualTo(custom)
    assertThat(resolved.lexicon?.thing?.singular).isEqualTo("glider")
  }

  @Test
  fun aThingWithoutDnaResolvesToAirplane() {
    // Every Thing created before templates existed. A closed set, and all of them airplanes —
    // which is why no stored hint is needed and Thing.template_id could be removed.
    // Fields 2-6 are reserved (#668); a legacy Thing is now simply one with no `template`.
    val legacy = Thing(id = "t1")

    assertThat(registry.forThingWithFallback(legacy)).isEqualTo(AirplaneTemplate.TEMPLATE)
  }

  @Test
  fun resolvingAThingWithoutDnaNeverFails() {
    // Deliberately not the CollectionKind.fromWire behaviour. An unknown collection means a
    // corrupt database and should throw; a missing template is ordinary and has a right answer,
    // so throwing here would turn a legacy Thing into a crash.
    assertThat(registry.forThingWithFallback(Thing())).isNotNull()
  }

  @Test
  fun dnaWinsOverTheBakedInPresetEvenAtTheSameId() {
    // A customised template shares its id with the canonical one it came from. If resolution
    // consulted the pool by id instead of reading the field, every customisation would silently
    // revert — the exact failure the DNA model exists to prevent.
    val customised = AirplaneTemplate.TEMPLATE.copy(
      lexicon = AirplaneTemplate.AIRPLANE_LEXICON.copy(
        squawk = Noun(singular = "gripe", plural = "gripes", article = "a"),
      ),
    )

    val resolved =
      registry.forThingWithFallback(Thing(id = "t1", template = customised))

    assertThat(resolved.lexicon?.squawk?.singular).isEqualTo("gripe")
    assertThat(resolved).isNotEqualTo(AirplaneTemplate.TEMPLATE)
  }

  @Test
  fun theCanonicalPoolOffersEveryBakedInPresetInPickerOrder() {
    // canonical() is what a picker offers, so the order is the templates' own sort_order rather
    // than declaration order — the list this returns is the list the user reads top to bottom.
    assertThat(
      registry.canonical()
        .map { it.id })
      .containsExactly(
        "airplane",
        "automotive",
        "bike",
        "boat",
        "home",
        "custom"
      )
      .inOrder()
    assertThat(registry.canonicalById("airplane")).isEqualTo(AirplaneTemplate.TEMPLATE)
    assertThat(registry.canonicalById("boat")?.id).isEqualTo("boat")
  }

  @Test
  fun anIdNoPresetClaimsStillResolvesToNothing() {
    // Null is the answer a fetched-template lookup will need once #726 exists; it must not start
    // returning a fallback just because the pool grew.
    assertThat(registry.canonicalById("submarine")).isNull()
  }
}

/** The airplane preset itself — the values Phase 2's string work is checked against. */
class AirplaneTemplateTest {

  @Test
  fun everyLexiconNounIsPopulated() {
    // A blank noun renders as an empty string rather than failing, so nothing would catch it until
    // a screen read "Add ". Checked here because the lexicon is hand-authored.
    val lexicon = AirplaneTemplate.AIRPLANE_LEXICON
    val nouns = listOf(
      lexicon.thing, lexicon.squawk, lexicon.task,
      lexicon.log, lexicon.component, lexicon.technician,
    )
    for (noun in nouns) {
      assertThat(noun).isNotNull()
      assertThat(noun!!.singular).isNotEmpty()
      assertThat(noun.plural).isNotEmpty()
      assertThat(noun.article).isNotEmpty()
    }
  }

  @Test
  fun everyLexiconStringIsPopulated() {
    val l = AirplaneTemplate.AIRPLANE_LEXICON
    assertThat(
      listOf(
        l.ready_status, l.down_status, l.down_status_long, l.collection_label,
        l.compliance_mandatory, l.compliance_advisory, l.authority_label,
      ),
    ).doesNotContain("")
  }

  @Test
  fun aircraftIsItsOwnPluralAndTakesAn() {
    // The case that makes `article` a stored field rather than something derived from the first
    // letter, and the case a naive pluraliser gets wrong.
    val thing = AirplaneTemplate.AIRPLANE_LEXICON.thing!!
    assertThat(thing.singular).isEqualTo("aircraft")
    assertThat(thing.plural).isEqualTo("aircraft")
    assertThat(thing.article).isEqualTo("an")
  }

  @Test
  fun everyCapabilityIsOn() {
    // Phase 2's premise: the flags are consulted everywhere and always answer the same way, so
    // wiring them changes no pixel (#659, #660). If this fails, Phase 2 is no longer invisible.
    val c = AirplaneTemplate.AIRPLANE_CAPABILITIES
    assertThat(
      listOf(
        c.components, c.meters, c.compliance, c.technicians,
        c.technician_certificates, c.component_serial_prompt,
      ),
    ).doesNotContain(false)
  }

  @Test
  fun allFourPerThingSectionsAppearInShellOrder() {
    // Mirrors PER_THING_SECTIONS in AdaptiveAppShell. Order is the contract, not just membership —
    // this list is what the shell renders.
    assertThat(AirplaneTemplate.AIRPLANE_CAPABILITIES.sections).containsExactly(
      Section.SECTION_DASHBOARD,
      Section.SECTION_SQUAWKS,
      Section.SECTION_TASKS,
      Section.SECTION_LOGS,
    )
      .inOrder()
  }

  @Test
  fun theComponentTreeFollowsHowThePartsAttach() {
    // Engine at the root, propeller on the engine, blades on the propeller (#729).
    //
    // **This deliberately no longer matches what Phase 1's cutover stored.** That backfill built
    // airframe -> engine -> propeller -> hub/blades, so every Thing in production still carries an
    // airframe wrapper and a separate hub. Reconciling them is the one-off migration this change
    // is paired with; until it runs, an aircraft stored under the old shape walks these slots and
    // matches nothing.
    val engine = AirplaneTemplate.AIRPLANE_COMPONENT_SLOTS.single()
    assertThat(engine.slot_key).isEqualTo("engine")
    assertThat(engine.repeatable).isTrue()

    val propeller = engine.children.single()
    assertThat(propeller.slot_key).isEqualTo("propeller")
    // The propeller carries the make, model and serial the hub used to: in a working aeroplane
    // they are the same part, and asking for both asked the same question twice.
    assertThat(propeller.serial_expected).isTrue()

    val blade = propeller.children.single()
    assertThat(blade.slot_key).isEqualTo("blade")
    assertThat(blade.repeatable).isTrue()
  }

  @Test
  fun specFieldKeysMatchTheLegacyThingFields() {
    // Phase 1 mirrored Thing fields 2-5 into `spec` under exactly these keys. A mismatch would
    // orphan every migrated value.
    assertThat(AirplaneTemplate.AIRPLANE_SPEC_FIELDS.map { it.key })
      .containsExactly("tail_number", "make", "model", "serial")
  }

  @Test
  fun engineAndPropMetersAreScopedToTheirComponents() {
    // Engine hours belong to the engine, not the airframe. `float interval_hours` could not say
    // so, which is why MeterRule is a new message rather than a renamed EngineHourRule.
    val meters = AirplaneTemplate.AIRPLANE_METERS.associateBy { it.key }
    assertThat(meters.getValue("airframe_hours").component_slot_key).isEmpty()
    assertThat(meters.getValue("engine_hours").component_slot_key).isEqualTo("engine")
    assertThat(meters.getValue("prop_hours").component_slot_key).isEqualTo("propeller")
  }

  @Test
  fun theBakedInPresetHasNoVersionFloor() {
    // A baked-in template ships inside the build that reads it, so it can never be from the
    // future. The floor exists for fetched templates.
    assertThat(AirplaneTemplate.TEMPLATE.min_app_version).isEqualTo(0)
  }
}
