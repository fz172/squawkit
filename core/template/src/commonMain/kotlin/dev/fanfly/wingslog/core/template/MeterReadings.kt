package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.ManualMeterReading
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
 * A meter’s current reading, and where it came from.
 *
 * @property asOf the day it was read — a log’s work date, or the day a manual reading was set. Null
 *   for a log that carries no date.
 * @property isManual set by hand on the dashboard rather than recorded on a log.
 */
data class CurrentReading(
  val meterKey: String,
  val value: Double,
  val asOf: LocalDate?,
  val isManual: Boolean,
)

/**
 * The current reading of every meter [logs] and [manual] carry: per key, the **most recent** one
 * (#1368).
 *
 * It was the maximum over the logs, which no later reading could ever lower — so a mistyped log
 * stuck until it was found and fixed, and a replaced tach could not be recorded at all. Recency is
 * decided in three steps:
 *
 * 1. **The later day wins** — a log’s work date against the day a manual reading was set, both read
 *    in [timeZone]. A log backdated to last month never moves the reading back.
 * 2. **Within a day, the last one saved wins** — a log’s `readings_saved_at` against the manual
 *    reading’s `set_at`. A log’s `timestamp` is midnight of its work date and cannot order this. A
 *    log saved before `readings_saved_at` existed has none and loses to anything that does, which is
 *    right: whatever has one was saved after it.
 * 3. **Two that still tie fall back to the higher value** — two such older logs on one day, where
 *    the maximum is all there ever was to go on.
 *
 * **Keyed off the readings rather than off a template**, deliberately: the overview is written by
 * the log manager, which has no Thing in hand, and a reading whose meter a template later stops
 * declaring is still the user’s data. The dashboard filters to what its template declares at render.
 *
 * A meter nothing has recorded is absent rather than zero, so a reader can tell "not recorded yet"
 * from "reads zero".
 */
fun currentReadingStates(
  logs: List<MaintenanceLog>,
  manual: List<ManualMeterReading> = emptyList(),
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<CurrentReading> {
  val candidates = buildList {
    logs.forEach { log ->
      val day = log.timestamp?.toLocalDate(timeZone)
      val savedAt = log.readings_saved_at?.toInstant()
      log.readings.map { it.meter_key }
        .distinct()
        .forEach { key ->
          log.readingFor(key)
            ?.let { add(Candidate(key, it, day, savedAt, isManual = false)) }
        }
    }
    manual.forEach { entry ->
      val reading = entry.reading ?: return@forEach
      val setAt = entry.set_at?.toInstant()
      // Zero is "not recorded" here as on a log, so the two can never disagree about it.
      if (reading.value_ > 0.0) add(
        Candidate(
          meterKey = reading.meter_key,
          value = reading.value_,
          day = setAt?.toLocalDateTime(timeZone)?.date,
          savedAt = setAt,
          isManual = true,
        )
      )
    }
  }
  return candidates.groupBy { it.meterKey }
    .toList()
    .sortedBy { (key, _) -> key }
    .map { (key, forKey) ->
      val latest = forKey.maxWith(MOST_RECENT)
      CurrentReading(key, latest.value, latest.day, latest.isManual)
    }
}

/** [currentReadingStates] as the bare key and value the overview stores. */
fun currentReadings(
  logs: List<MaintenanceLog>,
  manual: List<ManualMeterReading> = emptyList(),
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<MeterReading> =
  currentReadingStates(logs, manual, timeZone)
    .map { MeterReading(meter_key = it.meterKey, value_ = it.value) }

/** One reading competing to be a meter’s current one. */
private data class Candidate(
  val meterKey: String,
  val value: Double,
  val day: LocalDate?,
  val savedAt: Instant?,
  val isManual: Boolean,
)

// nullsFirst: a reading with no day, or no saved-at, is the oldest there is.
private val MOST_RECENT: Comparator<Candidate> =
  compareBy<Candidate, LocalDate?>(nullsFirst()) { it.day }
    .thenBy(nullsFirst()) { it.savedAt }
    .thenBy { it.value }

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
