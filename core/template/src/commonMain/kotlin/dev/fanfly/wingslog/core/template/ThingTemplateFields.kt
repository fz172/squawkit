package dev.fanfly.wingslog.core.template

import androidx.compose.runtime.staticCompositionLocalOf
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * The template in scope for the composable being rendered (#703).
 *
 * Sibling of [LocalThingLexicon] and [LocalThingCapabilities], provided from the same place at the
 * same moment, because the three answer one question between them: the lexicon supplies the words
 * for concepts every template shares, capabilities say which features exist, and this supplies the
 * **fields a specific template declares** — its meters, slots and spec fields.
 *
 * Null when nothing is selected, which is why every accessor below takes a fallback: an
 * account-level screen has no template and must still render a label.
 */
val LocalThingTemplate = staticCompositionLocalOf<ThingTemplate?> { null }

/**
 * True when `ComponentType` — the frozen airframe / engine / propeller enum — describes this
 * template's parts.
 *
 * The task and log forms ask "which component?" with a control whose three options are that enum,
 * and the enum is aviation's alone: PRD §6 freezes it rather than extending it, because an enum
 * can never cover the slots a template declares. Offering it to a boat or a car asks a question
 * with no true answer, and to a home a question about parts it does not have — so those presets
 * get **no picker at all** and the record is filed against the thing itself, which is what every
 * task on a preset with no components has always meant.
 *
 * **Null counts as aviation.** A template reaches the composition from the selected Thing's own
 * DNA, and a Thing migrated by the cutover carries none (see [ThingInflater]) — it predates the
 * pivot, so it can only be an aeroplane. Reading null as "not aviation" would take the picker away
 * from exactly the accounts that have always had it.
 */
val ThingTemplate?.usesComponentTypes: Boolean
  get() = this == null || id == AirplaneTemplate.ID

/** The meter [key] names, or null when this template does not declare it. */
fun ThingTemplate?.meter(key: String): MeterDef? =
  this?.meters?.firstOrNull { it.key == key }

/** The spec field [key] names, or null when this template does not declare it. */
fun ThingTemplate?.specField(key: String): SpecField? =
  this?.spec_fields?.firstOrNull { it.key == key }

/**
 * The slot [key] names, searched through the whole tree rather than the top level.
 *
 * Slots nest — an airplane's engine lives under its airframe — so a top-level scan would find
 * "airframe" and miss every component a log actually attaches to.
 */
fun ThingTemplate?.slot(key: String): ComponentSlot? {
  fun find(slots: List<ComponentSlot>): ComponentSlot? {
    slots.forEach { slot ->
      if (slot.slot_key == key) return slot
      find(slot.children)?.let { return it }
    }
    return null
  }
  return find(this?.component_slots.orEmpty())
}

/**
 * A meter's label, falling back to [ifAbsent] when the template does not declare it.
 *
 * **The fallback is not decoration.** These call sites are aviation-shaped screens that #729 and
 * #730 replace with template-driven ones; until then a preset that declares no `airframe_hours`
 * still reaches them, and a blank label is worse than the shipped string. When those screens go,
 * so do the fallbacks.
 */
fun ThingTemplate?.meterLabel(key: String, ifAbsent: String): String =
  meter(key)?.label?.takeIf { it.isNotEmpty() } ?: ifAbsent

/** A meter's label with its unit — "Airframe Time (hrs)". See [meterLabel] on the fallback. */
fun ThingTemplate?.meterLabelWithUnit(key: String, ifAbsent: String): String {
  val meter = meter(key) ?: return ifAbsent
  val label = meter.label.takeIf { it.isNotEmpty() } ?: return ifAbsent
  return meter.unit_label.takeIf { it.isNotEmpty() }
    ?.let { "$label ($it)" } ?: label
}

/** A slot's label, falling back to [ifAbsent]. See [meterLabel] on why the fallback exists. */
fun ThingTemplate?.slotLabel(key: String, ifAbsent: String): String =
  slot(key)?.label?.takeIf { it.isNotEmpty() } ?: ifAbsent

/** A spec field's label, falling back to [ifAbsent]. See [meterLabel] on why the fallback exists. */
fun ThingTemplate?.specLabel(key: String, ifAbsent: String): String =
  specField(key)?.label?.takeIf { it.isNotEmpty() } ?: ifAbsent

/**
 * "<slot label> Serial" — the caption on a component's serial field.
 *
 * Composed rather than stored because a slot label is a noun the template already declares, and a
 * second string per slot would be one more thing to keep in step with it.
 */
fun ThingTemplate?.slotSerialLabel(key: String, ifAbsent: String): String =
  slot(key)?.label?.takeIf { it.isNotEmpty() }
    ?.let { "$it Serial" } ?: ifAbsent

/**
 * Meter keys the canonical presets declare.
 *
 * A key is a join: a template's `MeterDef.key`, a log's `MeterReading.meter_key` and a
 * `MeterRule.meter_key` all have to spell it the same way, and a typo is silent — the reading is
 * kept, it just never matches a rule. Naming them here is what keeps the three in step.
 *
 * The three hour keys are also the only ones any log stored before `readings` existed has values
 * for, which is why the read fallbacks in `MeterReadings` name exactly these.
 */
object MeterKeys {
  const val AIRFRAME_HOURS = "airframe_hours"
  const val ENGINE_HOURS = "engine_hours"
  const val PROP_HOURS = "prop_hours"

  /** Automotive and bike. Miles, no decimal — "84512.0 mi" is not how anyone writes mileage. */
  const val ODOMETER = "odometer"
  const val RIDE_HOURS = "ride_hours"
}
