package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.ThingTemplate

/**
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

/**
 * A meter value with its unit — "5000 MI", "100.0 HRS".
 *
 * Every renderer of a due value hardcoded "HRS", so a car scheduled every 5,000 miles read
 * "5000.0 HRS" on its card while the editor that created it said "mi" (#759). The unit belongs to
 * the meter, and so does whether it takes a decimal point.
 *
 * [meterKey] null or unknown falls back to hours, which is what every value written before meter
 * rules existed meant.
 */
fun ThingTemplate?.formatMeterValue(meterKey: String?, value: Double): String {
  val meter =
    meterKey?.let { key -> this?.meters?.firstOrNull { it.key == key } }
  val unit = meter?.unit_label?.takeIf { it.isNotEmpty() } ?: "hrs"
  val text = if (meter?.decimal == false) {
    value.toLong()
      .toString()
  } else {
    val rounded = (value * 10).toLong() / 10.0
    if (rounded == rounded.toLong()
        .toDouble()
    ) "${rounded.toLong()}.0" else rounded.toString()
  }
  return "$text ${unit.uppercase()}"
}
