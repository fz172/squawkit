package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * The PRD §4.7 validation rules, run over every baked-in preset (#721, #722, #723).
 *
 * "Built-in presets are validated by a unit test that loads all of them" — this is that test. It
 * reads [CanonicalTemplates.ALL], so a preset added there is validated without anyone remembering
 * to extend anything.
 */
class CanonicalTemplatesTest {

  private val all = CanonicalTemplates.ALL

  private fun ThingTemplate.allSlots(): List<ComponentSlot> {
    fun flatten(slots: List<ComponentSlot>): List<ComponentSlot> =
      slots.flatMap { listOf(it) + flatten(it.children) }
    return flatten(component_slots)
  }

  @Test
  fun everyPresetIsRegistered() {
    // Car and motorcycle are one `automotive` preset; PRD §4.8 lists them separately and is stale.
    assertThat(all.map { it.id }).containsExactly(
      "airplane", "automotive", "bike", "boat", "home", "custom",
    )
  }

  @Test
  fun idsAndSortOrdersAreUnique() {
    // A duplicate id silently shadows a preset in the registry's `associateBy`; a duplicate
    // sort_order makes the picker's order depend on list order, which is not a decision anyone made.
    assertThat(all.map { it.id }).containsNoDuplicates()
    assertThat(all.map { it.sort_order }).containsNoDuplicates()
  }

  @Test
  fun everyPresetShipsWithNoVersionFloor() {
    // A floor above 0 degrades the preset on the very build that carries it (#728). Baked-in
    // templates can never be newer than their reader.
    all.forEach { assertThat(it.min_app_version).isEqualTo(0) }
  }

  @Test
  fun everyYearIsAskedForAsANumber() {
    // A year on a text keyboard, capitalised like prose. `numeric` is the flag that says otherwise.
    val years = all.flatMap { it.spec_fields }
      .filter { it.key == "year" || it.key == "year_built" }

    assertThat(years).isNotEmpty()
    years.forEach { assertThat(it.numeric).isTrue() }
  }

  @Test
  fun everyPresetIsIdentifiable() {
    all.forEach { template ->
      assertThat(template.id).isNotEmpty()
      assertThat(template.version).isGreaterThan(0)
      assertThat(template.display_name).isNotEmpty()
      assertThat(template.icon).isNotEmpty()
    }
  }

  /** §4.7: "a config is invalid if it ships an empty lexicon noun." */
  @Test
  fun noPresetShipsAnEmptyLexiconNoun() {
    all.forEach { template ->
      val lexicon = checkNotNull(template.lexicon) { "${template.id} has no lexicon" }
      val nouns = listOf(
        "thing" to lexicon.thing,
        "squawk" to lexicon.squawk,
        "task" to lexicon.task,
        "log" to lexicon.log,
        "component" to lexicon.component,
        "technician" to lexicon.technician,
      )
      nouns.forEach { (field, noun) ->
        assertThat(checkNotNull(noun) { "${template.id}.$field is absent" }.singular)
          .isNotEmpty()
        assertThat(noun.plural).isNotEmpty()
        // A wrong article is visible in every empty state, and it is not derivable from the first
        // letter — "an hour", "a unicycle".
        assertThat(noun.article).isNotEmpty()
      }
    }
  }

  /** §4.7: "invalid if it enables METER rules while declaring no meters." */
  @Test
  fun noPresetClaimsMetersWithoutDeclaringAny() {
    all.forEach { template ->
      if (template.capabilities?.meters == true) {
        assertThat(template.meters).isNotEmpty()
      }
    }
  }

  @Test
  fun noPresetClaimsComponentsWithoutDeclaringSlots() {
    // The mirror of the meter rule: a component tree UI over zero slots is an empty screen with a
    // heading, which is worse than the section not existing.
    all.forEach { template ->
      if (template.capabilities?.components == true) {
        assertThat(template.component_slots).isNotEmpty()
      }
    }
  }

  /** §4.7: "invalid if it references a meter_key or spec_key that does not exist." */
  @Test
  fun everyMeterScopesToASlotTheTemplateDeclares() {
    all.forEach { template ->
      val slots = template.allSlots().map { it.slot_key }.toSet()
      template.meters.forEach { meter ->
        if (meter.component_slot_key.isNotEmpty()) {
          assertThat(slots).contains(meter.component_slot_key)
        }
      }
    }
  }

  @Test
  fun keysAreUniqueWithinEachPreset() {
    all.forEach { template ->
      assertThat(template.spec_fields.map { it.key }).containsNoDuplicates()
      assertThat(template.meters.map { it.key }).containsNoDuplicates()
      // Slot keys form the stored component id ("$thingId:engine.1.blade.0"), so a duplicate at the
      // same level would make two components share an id.
      assertThat(template.allSlots().map { it.slot_key }).containsNoDuplicates()
    }
  }

  @Test
  fun everyDeclaredKeyAndLabelIsNonEmpty() {
    all.forEach { template ->
      template.spec_fields.forEach {
        assertThat(it.key).isNotEmpty()
        assertThat(it.label).isNotEmpty()
      }
      template.meters.forEach {
        assertThat(it.key).isNotEmpty()
        assertThat(it.label).isNotEmpty()
        assertThat(it.unit_label).isNotEmpty()
      }
      template.allSlots().forEach {
        assertThat(it.slot_key).isNotEmpty()
        assertThat(it.label).isNotEmpty()
      }
    }
  }

  @Test
  fun everyPresetOffersSectionsAndPriorities() {
    // Empty `sections` would render a shell with no per-thing navigation at all; empty `priorities`
    // would leave the squawk form with nothing to pick.
    all.forEach { template ->
      val capabilities = checkNotNull(template.capabilities)
      assertThat(capabilities.sections).isNotEmpty()
      assertThat(capabilities.priorities).isNotEmpty()
      assertThat(capabilities.sections).containsNoDuplicates()
      assertThat(capabilities.priorities).containsNoDuplicates()
    }
  }

  @Test
  fun onlyTheAirplaneUsesTheLogbookExportLayout() {
    // The paper-logbook tab layout is aviation's; everything else derives columns from the lexicon
    // and meter set. A vehicle preset inheriting it would export empty tach columns.
    all.forEach { template ->
      val layout = template.capabilities?.export_layout?.name
      if (template.id == "airplane") {
        assertThat(layout).isEqualTo("EXPORT_LAYOUT_LOGBOOK")
      } else {
        assertThat(layout).isEqualTo("EXPORT_LAYOUT_GENERIC")
      }
    }
  }

  /**
   * The two presets whose whole value is what they leave out.
   *
   * Asserted by name rather than by rule because they are load-bearing in a way no general rule
   * expresses: `home` is what proves the system is not aviation with the nouns swapped
   * (`pivot_rollout_design.md` §5), and `custom` is the floor every screen has to survive. If a
   * later edit gives home a component tree or a meter, the preset stops doing its job and this is
   * what says so.
   */
  @Test
  fun homeAndCustomDeclareNothingTheyShouldNot() {
    val home = CanonicalTemplates.HOME
    assertThat(home.component_slots).isEmpty()
    assertThat(home.meters).isEmpty()
    assertThat(home.capabilities?.components).isFalse()
    assertThat(home.capabilities?.meters).isFalse()
    // A house has no make, no model and no serial — PRD §4.2's argument against a universal core.
    assertThat(home.spec_fields.map { it.key })
      .containsNoneOf("make", "model", "serial")

    val custom = CanonicalTemplates.CUSTOM
    // One field, and it is the name — without it the create form was empty and every Thing on
    // this preset stored no name at all. Everything else is still the user's to invent.
    assertThat(custom.spec_fields.map { it.key }).containsExactly("name")
    assertThat(custom.custom_spec_fields).isEqualTo(3)
    assertThat(custom.component_slots).isEmpty()
    assertThat(custom.meters).isEmpty()
  }

  @Test
  fun everyPresetDecodesWithoutUnknownFields() {
    // An unknown field means the committed .pb was compiled against a schema this build does not
    // have — a stale asset, since both come from this repo.
    all.forEach { template ->
      assertThat(template.unknownFields.size).isEqualTo(0)
      assertThat(template.capabilities?.unknownFields?.size ?: 0).isEqualTo(0)
      assertThat(template.lexicon?.unknownFields?.size ?: 0).isEqualTo(0)
    }
  }
}
