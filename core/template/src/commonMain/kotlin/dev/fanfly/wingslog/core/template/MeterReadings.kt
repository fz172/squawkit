package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.ThingTemplate

/**
 * Reading and writing meter values by key (#730).
 *
 * `readings` is the only place a value lives. The three aviation doubles it replaced were retired
 * in #761, after a backfill wrote the keyed form onto every stored record — so the read fallbacks
 * that stood here are gone with them, and a log that reports nothing for a meter genuinely has
 * nothing for it.
 */

/**
 * This log's value for [meterKey], or null when it recorded none.
 *
 * Null rather than 0.0 throughout: a log that did not touch a meter is not a log reporting zero,
 * and the difference decides whether it counts toward the current reading.
 */
fun MaintenanceLog.readingFor(meterKey: String): Double? =
  readings.firstOrNull { it.meter_key == meterKey }
    ?.value_
    ?.takeIf { it > 0.0 }

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
    logs.forEach { log -> log.readings.forEach { add(it.meter_key) } }
  }
  return keys.sorted()
    .mapNotNull { key ->
      val value = logs.mapNotNull { it.readingFor(key) }
        .maxOrNull() ?: return@mapNotNull null
      MeterReading(meter_key = key, value_ = value)
    }
}

/** The overview's current value for [meterKey], or null when no log has recorded one. */
fun MaintenanceOverview.currentFor(meterKey: String): Double? =
  current.firstOrNull { it.meter_key == meterKey }
    ?.value_
    ?.takeIf { it > 0.0 }

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
 * The one reading worth leading with for [log]: the meter of the component the work was done on,
 * when the log recorded it, otherwise the first meter the template declares that it did record.
 *
 * Both the log detail sheet and the log rows show a single headline number. They once picked it by
 * switching on `component_type` across three aviation fields, so a car's log — an odometer reading —
 * matched no branch and displayed a blank (#761). Declaration order alone fixed that and broke the
 * airplane instead: an engine log that also noted airframe time led with the airframe's hours.
 * [meterForComponent] answers for the airplane; everywhere else it is the template's first meter,
 * which is where the fallback starts anyway.
 */
fun ThingTemplate?.primaryReading(log: MaintenanceLog): MeterValue? {
  val meters = this?.meters.orEmpty()
  val preferred = listOfNotNull(meterForComponent(log.component_type))
  return (preferred + meters).firstNotNullOfOrNull { meter ->
    log.readingFor(meter.key)
      ?.let { MeterValue(meter, it) }
  }
}

/**
 * What [log] recorded on the template's first meter — the one series a log timeline can run down
 * its gutter. Not [primaryReading]: that follows the component, and an unlabelled column that mixes
 * engine, propeller and airframe hours reads as one meter jumping about.
 */
fun ThingTemplate?.timelineReading(log: MaintenanceLog): MeterValue? {
  val meter = this?.meters?.firstOrNull() ?: return null
  return log.readingFor(meter.key)
    ?.let { MeterValue(meter, it) }
}
