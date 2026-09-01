package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.ThingTemplate

/**d
 * Reading and writing meter values by key, across the change from three hour fields to a declared
 * set (#730).
 *
 * **Every read falls back to the legacy fields.** `engine_hour`, `airframe_time` and `prop_time`
 * hold the value on every log written before this, and there are a lot of them. Migrating them is a
 * separate decision; until then a reader that only consulted `readings` would report an aeroplane
 * with years of history as having flown zero hours.
 */

/** The three keys whose values also live in the legacy fields, and always will for old logs. */
private val LEGACY_FIELD_KEYS = mapOf<String, (MaintenanceLog) -> Double>(
  MeterKeys.AIRFRAME_HOURS to { it.airframe_time },
  MeterKeys.ENGINE_HOURS to { it.engine_hour },
  MeterKeys.PROP_HOURS to { it.prop_time },
)

/**
 * This log's value for [meterKey], or null when it recorded none.
 *
 * Null rather than 0.0 throughout: a log that did not touch a meter is not a log reporting zero,
 * and the difference decides whether it counts toward the current reading.
 */
fun MaintenanceLog.readingFor(meterKey: String): Double? {
  readings.firstOrNull { it.meter_key == meterKey }
    ?.let { return it.value_.takeIf { value -> value > 0.0 } }
  return LEGACY_FIELD_KEYS[meterKey]?.invoke(this)
    ?.takeIf { it > 0.0 }
}

/**
 * The current reading for every meter [logs] carry — the maximum over them, per key.
 *
 * The same computation the three `current_*` fields have always used, generalised. **Keyed off the
 * logs rather than off a template**, deliberately: the overview is written by the log manager,
 * which has no Thing in hand, and a reading whose meter a template later stops declaring is still
 * the user's data. The dashboard filters to what its template declares at render.
 *
 * A meter no log has touched is absent rather than zero, so a reader can tell "not recorded yet"
 * from "reads zero".
 */
fun currentReadings(logs: List<MaintenanceLog>): List<MeterReading> {
  val keys = buildSet {
    logs.forEach { log ->
      log.readings.forEach { add(it.meter_key) }
      LEGACY_FIELD_KEYS.forEach { (key, read) -> if (read(log) > 0.0) add(key) }
    }
  }
  return keys.sorted()
    .mapNotNull { key ->
      val value = logs.mapNotNull { it.readingFor(key) }
        .maxOrNull() ?: return@mapNotNull null
      MeterReading(meter_key = key, value_ = value)
    }
}

/**
 * The overview's current value for [meterKey], from the declared set or the legacy fields.
 *
 * The fallback matters for an overview computed by a build that predates `current` — it is
 * recomputed on the next log write, but until then the three doubles are all it has.
 */
fun MaintenanceOverview.currentFor(meterKey: String): Double? {
  current.firstOrNull { it.meter_key == meterKey }
    ?.let { return it.value_.takeIf { value -> value > 0.0 } }
  return when (meterKey) {
    MeterKeys.AIRFRAME_HOURS -> current_airframe_time
    MeterKeys.ENGINE_HOURS -> current_engine_time
    MeterKeys.PROP_HOURS -> current_propeller_time
    else -> null
  }?.takeIf { it > 0.0 }
}

/**
 * [readings] with [meterKey] set to [value], or removed when it is null.
 *
 * Removal rather than a zero entry: clearing a meter field on the form means "I did not record
 * this", which is what an absent reading says and what a zero one does not.
 */
fun List<MeterReading>.withReading(
  meterKey: String,
  value: Double?,
): List<MeterReading> {
  val without = filterNot { it.meter_key == meterKey }
  return if (value == null) without else without + MeterReading(
    meterKey,
    value_ = value
  )
}

/** The meter [meterKey] names, or null when this template does not declare it. */
private fun ThingTemplate?.meterDef(meterKey: String?): MeterDef? =
  meterKey?.let { key -> this?.meters?.firstOrNull { it.key == key } }

/**
 * A meter value on its own — "5000", "100.0".
 *
 * The decimal place is the meter's call: an odometer takes none, and "84512.0 mi" is not how
 * anyone writes mileage. Split from [formatMeterValue] for the layouts that render the number and
 * the unit as separate baseline-aligned text.
 */
fun ThingTemplate?.formatMeterNumber(meterKey: String?, value: Double): String {
  if (meterDef(meterKey)?.decimal == false) return value.toLong()
    .toString()
  val rounded = (value * 10).toLong() / 10.0
  return if (rounded == rounded.toLong()
      .toDouble()
  ) "${rounded.toLong()}.0" else rounded.toString()
}

/**
 * The unit a meter is measured in, upper-cased — "MI", "HRS".
 *
 * Falls back to hours for a key this template does not declare, which is what every value written
 * before meter rules existed meant.
 */
fun ThingTemplate?.meterUnit(meterKey: String?): String =
  (meterDef(meterKey)?.unit_label?.takeIf { it.isNotEmpty() }
    ?: "hrs").uppercase()

/**
 * A meter value with its unit — "5000 MI", "100.0 HRS".
 *
 * Every renderer of a due value hardcoded "HRS", so a car scheduled every 5,000 miles read
 * "5000.0 HRS" on its card while the editor that created it said "mi" (#759).
 */
fun ThingTemplate?.formatMeterValue(meterKey: String?, value: Double): String =
  "${formatMeterNumber(meterKey, value)} ${meterUnit(meterKey)}"

/**
 * The one reading worth leading with for [log] — the first meter the template declares that this
 * log actually recorded.
 *
 * Both the log detail sheet and the dashboard's recent-activity row show a single headline number.
 * They picked it from `component_type`, which meant an aeroplane's three hour fields and nothing
 * else: a car's log recorded an odometer reading and displayed a blank, because none of the three
 * branches matched and all three doubles were zero (#761).
 *
 * Declaration order decides, so a template leads with the meter it lists first.
 */
fun ThingTemplate?.primaryReading(log: MaintenanceLog): Pair<MeterDef, Double>? =
  this?.meters.orEmpty()
    .firstNotNullOfOrNull { meter ->
      log.readingFor(meter.key)
        ?.let { meter to it }
    }
