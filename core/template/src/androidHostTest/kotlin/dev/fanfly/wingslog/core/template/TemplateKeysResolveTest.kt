package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.ThingTemplate
import org.junit.Test

/**
 * That every key a template declares actually resolves.
 *
 * **A preset with a wrong key is a broken screen, not a failed build.** Nothing in the type system
 * connects `"airframe_hours"` in a template to the code that reads it, or a meter's
 * `component_slot_key` to the slot it names — they are strings on both sides. The failure is silent
 * and the data is *wrong* rather than absent, which is the same shape as the GA4 naming problem
 * `AnalyticsTaxonomyTest` guards, and it is guarded here for the same reason.
 *
 * This runs over **every template in the pool**, so it covers the six Phase 3 presets as they land
 * without anyone remembering to extend it. Today the pool is airplane alone, which is exactly when
 * the checks are cheapest to get right.
 */
class TemplateKeysResolveTest {

  private val pool: List<ThingTemplate> =
    BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE).canonical()

  private fun ThingTemplate.allSlots(): List<ComponentSlot> {
    fun flatten(slots: List<ComponentSlot>): List<ComponentSlot> =
      slots.flatMap { listOf(it) + flatten(it.children) }
    return flatten(component_slots)
  }

  @Test
  fun thePoolIsNotEmpty() {
    // Every assertion below is vacuously true over an empty pool. If the registry ever stops
    // returning the baked-in presets, this is what says so rather than the suite going quietly
    // green.
    assertThat(pool).isNotEmpty()
  }

  @Test
  fun everyMeterNamesASlotThatExists() {
    // The check with real teeth. A meter whose `component_slot_key` names no declared slot has
    // nowhere to render — an engine-hours meter on a template with no engine slot is a reading the
    // UI cannot attach to anything.
    val offenders = pool.flatMap { template ->
      val slotKeys = template.allSlots()
        .map { it.slot_key }
        .toSet()
      template.meters
        .filter { it.component_slot_key.isNotEmpty() && it.component_slot_key !in slotKeys }
        .map { "${template.id}: meter '${it.key}' -> slot '${it.component_slot_key}'" }
    }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun noDeclaredKeyIsBlank() {
    val offenders = pool.flatMap { template ->
      buildList {
        template.spec_fields.filter { it.key.isEmpty() }
          .forEach { add("${template.id}: spec field with blank key (label '${it.label}')") }
        template.meters.filter { it.key.isEmpty() }
          .forEach { add("${template.id}: meter with blank key (label '${it.label}')") }
        template.allSlots()
          .filter { it.slot_key.isEmpty() }
          .forEach { add("${template.id}: component slot with blank key (label '${it.label}')") }
      }
    }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun keysAreUniqueWithinTheirKind() {
    // A duplicate is worse than a missing key: two spec fields sharing `"serial"` both write to the
    // same `Spec`, so whichever renders second wins and the first silently does nothing.
    val offenders = pool.flatMap { template ->
      buildList {
        addAll(
          duplicatesOf(
            template.id,
            "spec field",
            template.spec_fields.map(SpecField::key)
          )
        )
        addAll(
          duplicatesOf(
            template.id,
            "meter",
            template.meters.map(MeterDef::key)
          )
        )
        addAll(
          duplicatesOf(
            template.id,
            "component slot",
            template.allSlots()
              .map { it.slot_key })
        )
      }
    }

    assertThat(offenders).isEmpty()
  }

  private fun duplicatesOf(
    templateId: String,
    kind: String,
    keys: List<String>
  ): List<String> =
    keys.groupingBy { it }
      .eachCount()
      .filterValues { it > 1 }
      .keys
      .map { "$templateId: duplicate $kind key '$it'" }

  @Test
  fun everyTemplateHasAnIdAndADisplayName() {
    // `id` is the picker's identity and the `template_id` every Thing-scoped analytics event
    // carries (#666); `display_name` is what the picker shows. Neither has a sensible default.
    val offenders = pool
      .filter { it.id.isEmpty() || it.display_name.isEmpty() }
      .map { "id='${it.id}' display_name='${it.display_name}'" }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun noTwoTemplatesShareAnId() {
    val duplicates = pool.map { it.id }
      .groupingBy { it }
      .eachCount()
      .filterValues { it > 1 }.keys

    assertThat(duplicates).isEmpty()
  }

  /**
   * The coupling that is left: `ThingInflater` names a new Thing from `tail_number`, else make and
   * model. #729 took the slot keys and #739 the casing rule; these four are what remain.
   */
  @Test
  fun theAirplaneEditFormUsesOnlySpecKeysTheTemplateDeclares() {
    val airplane = pool.single { it.id == AirplaneTemplate.ID }
    val declared = airplane.spec_fields.map { it.key }
      .toSet()

    val emitted = setOf("make", "model", "serial", "tail_number")

    assertThat(declared).containsAtLeastElementsIn(emitted)
  }
}
