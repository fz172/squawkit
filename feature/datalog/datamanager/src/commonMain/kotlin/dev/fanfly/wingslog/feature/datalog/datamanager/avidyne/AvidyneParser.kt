package dev.fanfly.wingslog.feature.datalog.datamanager.avidyne

import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.datalog.DataLogSource
import dev.fanfly.wingslog.feature.datalog.datamanager.CanonicalSeriesRegistry
import dev.fanfly.wingslog.feature.datalog.datamanager.Confidence
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParseException
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParser
import dev.fanfly.wingslog.feature.datalog.datamanager.avidyne.AvidyneParser.Companion.UNITS
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.Breather
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.ColumnAccumulator
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.LineCursor
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.decodeText
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.forwardFilled
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseDoubleAt
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseIntAt
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.NumericColumn
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog
import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.abs

/**
 * Avidyne Entegra / EX5000 `Engine_*_out.log` (design §6.2).
 *
 * Three header lines and then rows:
 *
 * ```
 * Avidyne Engine Data Log; DAU Software ID: 5.0
 * 3/13/12 18:41:05
 * "TIME","LAT","LON","PALT","E1",…
 * 18:41:06,-0.0000,-0.0000,1172,1006,…
 * ```
 *
 * **Every row carries a time of day and no date.** The only date in the file is on line 2, so a log
 * that runs past midnight has to be noticed: the clock going backwards by most of a day is the
 * rollover, and the date advances. A clock going backwards by a minute is not — an Avidyne data
 * acquisition unit corrects its clock mid-log, and one of the samples this was written against does
 * exactly that four rows in. Reading that as a rollover would have dated the rest of the flight to
 * the following day.
 *
 * **No column states a unit.** The names are a fixed vocabulary rather than free text, so the units
 * come from [UNITS] — and every one of them is settled by the data rather than assumed. Exhaust
 * temperatures reach 1,500 and cylinder heads 500, which are Fahrenheit numbers; a Celsius exhaust
 * gas temperature is around 800.
 */
class AvidyneParser : DataLogParser {

  override val formats: Set<DataLogFormat> =
    setOf(DataLogFormat.DATA_LOG_FORMAT_AVIDYNE)

  /**
   * 1 the first release.
   *
   * **Bump this in the same commit as any change to what `parse` emits** — a value, a unit, a name,
   * a canonical id, the set of columns — and raise the pinned number in `ParserVersionsTest` with
   * it. A stored record keeps the catalogue it was imported with and `DataLogManagerImpl.load`
   * rewrites it only when this number has moved.
   */
  override val version: Int = 1

  override fun sniff(header: ByteArray): Confidence {
    val text = decodeText(header)
      .removePrefix(BOM)
    val first = text.substringBefore('\n')
      .trimEnd('\r')
    return if (first.startsWith(TITLE)) Confidence.DEFINITE else Confidence.NONE
  }

  /** An Avidyne file is one recording, so this is always a list of one — or none, past its end. */
  override suspend fun parse(
    bytes: ByteArray,
    fileName: String,
    session: Int?,
  ): List<ParsedDataLog> {
    if (session != null && session != 0) return emptyList()
    return listOf(parseOne(bytes))
  }

  private suspend fun parseOne(bytes: ByteArray): ParsedDataLog {
    val text = decodeText(bytes)
      .removePrefix(BOM)
    val lines = LineCursor(text)
    val title = lines.next()
      ?.takeIf { it.startsWith(TITLE) }
      ?: throw DataLogParseException("not an Avidyne log: missing \"$TITLE\" title")
    val stamped = lines.next()
      ?.let(::parseHeaderStamp)
      ?: throw DataLogParseException("not an Avidyne log: no start date on line 2")
    val names = lines.next()
      ?.split(',')
      ?.map {
        it.trim()
          .trim('"')
      }
      ?: throw DataLogParseException("missing column names")
    val columnCount = names.size
    if (columnCount < 2) throw DataLogParseException("no columns")

    val timeCol = names.indexOf(TIME_NAME)
    if (timeCol < 0) throw DataLogParseException("no $TIME_NAME column")
    val latCol = names.indexOf(LATITUDE_NAME)
    val lonCol = names.indexOf(LONGITUDE_NAME)
    val hasPosition = latCol >= 0 && lonCol >= 0
    val skip = BooleanArray(columnCount).also {
      it[timeCol] = true
      if (hasPosition) it[lonCol] = true
    }

    val capacity = lines.remainingLineCount()
    val columns = arrayOfNulls<ColumnAccumulator>(columnCount)
    val secondsOfDay = IntArray(capacity)
    val dayOffsets = IntArray(capacity)
    val latitude =
      if (hasPosition) DoubleArray(capacity) { Double.NaN } else null
    val longitude =
      if (hasPosition) DoubleArray(capacity) { Double.NaN } else null
    var rows = 0
    var previousClock = -1
    var day = 0
    var started = false
    val breather = Breather()

    while (true) {
      val line = lines.next() ?: break
      if (line.isEmpty()) continue

      // The clock decides whether this row is kept, so it is read before anything else is. Reading
      // it in column order instead meant a skipped row had already widened every column's range by
      // the time the decision was made — which is how the power-on frame's -40 outside air survived
      // being dropped.
      val clock = clockAt(line, timeCol)
      if (clock < 0) continue

      // The unit writes one frame of sensor defaults before the log it just announced — every
      // sentinel at once, and a clock a few seconds BEFORE the start on line 2. That last part is
      // what identifies it, so this drops a real artefact rather than guessing at the first row.
      // Leading only: a clock correction later in the file also reads as earlier than the start.
      if (!started) {
        if (clock < stamped.secondOfDay) continue
        started = true
      }

      var start = 0
      var col = 0
      var latitudeHere = Double.NaN
      var longitudeHere = Double.NaN
      while (col < columnCount && start <= line.length) {
        var end = line.indexOf(',', start)
        if (end < 0) end = line.length
        var s = start
        var e = end
        while (s < e && line[s] == ' ') s++
        while (e > s && line[e - 1] == ' ') e--
        if (e > s) {
          when {
            col == timeCol -> Unit
            hasPosition && col == latCol -> latitudeHere =
              parseDoubleAt(line, s, e)

            hasPosition && col == lonCol -> longitudeHere =
              parseDoubleAt(line, s, e)

            else -> {
              val quoted = line[s] == '"' && line[e - 1] == '"' && e - s >= 2
              val acc = columns[col]
                ?: ColumnAccumulator(capacity).also { columns[col] = it }
              if (quoted) {
                // The format quotes exactly the discrete-input and -output columns, whose values are
                // bit patterns. Read as numbers, "0001000" becomes one thousand and the chart draws
                // a switch closing as a spike to a thousand.
                acc.text(rows, line.substring(s + 1, e - 1))
              } else {
                val v = parseDoubleAt(line, s, e)
                if (v.isNaN()) acc.text(rows, line.substring(s, e))
                else acc.number(rows, v.toFloat())
              }
            }
          }
        }
        start = end + 1
        col++
      }

      if (previousClock >= 0 && clock < previousClock) {
        // Most of a day backwards is midnight; a minute backwards is the unit correcting itself.
        if (previousClock - clock >= ROLLOVER_SECONDS) day++
      }
      previousClock = clock
      secondsOfDay[rows] = clock
      dayOffsets[rows] = day
      if (hasPosition) {
        // 0,0 is what this unit writes with no fix, not a position in the Gulf of Guinea.
        if (abs(latitudeHere) > FIX_EPSILON || abs(longitudeHere) > FIX_EPSILON) {
          latitude!![rows] = latitudeHere
          longitude!![rows] = longitudeHere
        }
      }
      rows++
      breather.breathe()
    }
    if (rows == 0) throw DataLogParseException("no rows")

    val startInstant = LocalDateTime(
      stamped.date,
      LocalTime.fromSecondOfDay(secondsOfDay[0]),
    ).toInstant(TimeZone.UTC)
    val timeSeconds = IntArray(rows)
    for (i in 1 until rows) {
      val elapsed = (dayOffsets[i] - dayOffsets[0]) * SECONDS_PER_DAY +
        (secondsOfDay[i] - secondsOfDay[0])
      // A corrected clock keeps its row order and moves forward by a nominal step, the same rule
      // the Garmin parser uses for a GPS time step.
      timeSeconds[i] = maxOf(timeSeconds[i - 1] + 1, elapsed)
    }

    val series = ArrayList<DataLogSeries>(columnCount)
    val numeric = LinkedHashMap<Int, NumericColumn>()
    val texts = LinkedHashMap<Int, Array<String?>>()
    var position: PositionColumn? = null
    for (col in 0 until columnCount) {
      if (hasPosition && col == latCol) {
        val lat = latitude!!.copyOf(rows)
        val lon = longitude!!.copyOf(rows)
        var fixes = 0
        for (i in 0 until rows) if (!lat[i].isNaN() && !lon[i].isNaN()) fixes++
        if (fixes == 0) continue
        position = PositionColumn(lat, lon)
        series += DataLogSeries(
          column = col,
          name = POSITION_NAME,
          short_name = POSITION_NAME,
          unit = "deg",
          kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION,
          canonical_id = CanonicalSeries.POSITION,
          sample_count = fixes,
        )
        continue
      }
      if (skip[col]) continue
      val acc = columns[col] ?: continue
      val name = names[col]
      val unit = UNITS[name].orEmpty()
      if (acc.numericCount > 0) {
        val raw = acc.floats!!.copyOf(rows)
        numeric[col] = NumericColumn(raw, forwardFilled(raw))
        series += DataLogSeries(
          column = col,
          name = name,
          short_name = name,
          unit = unit,
          kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          canonical_id = CanonicalSeriesRegistry.canonicalIdFor(name),
          min = acc.min.toDouble(),
          max = acc.max.toDouble(),
          sample_count = acc.numericCount,
        )
      } else {
        texts[col] = acc.texts!!.copyOf(rows)
        series += DataLogSeries(
          column = col,
          name = name,
          short_name = name,
          unit = unit,
          kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT,
          sample_count = acc.textCount,
        )
      }
    }

    return ParsedDataLog(
      format = DataLogFormat.DATA_LOG_FORMAT_AVIDYNE,
      parserVersion = version,
      source = DataLogSource(
        product = PRODUCT,
        software_version = softwareVersion(title),
      ),
      start = startInstant,
      // The file states no zone. Its clock is the aeroplane's local time, and reporting no offset is
      // what makes the viewer's clock axis read back the times the recorder actually wrote.
      utcOffsetMinutes = 0,
      durationSeconds = timeSeconds[rows - 1],
      sampleCount = rows,
      sampleRateHz = if (timeSeconds[rows - 1] > 0) {
        (rows - 1).toFloat() / timeSeconds[rows - 1]
      } else {
        1f
      },
      series = series,
      data = DataLogSeriesData(timeSeconds, numeric, texts, position),
    )
  }

  private class HeaderStamp(val date: LocalDate, val secondOfDay: Int)

  private companion object {
    const val BOM = "﻿"
    const val TITLE = "Avidyne Engine Data Log"
    const val PRODUCT = "Avidyne Entegra"
    const val TIME_NAME = "TIME"
    const val LATITUDE_NAME = "LAT"
    const val LONGITUDE_NAME = "LON"
    const val POSITION_NAME = "Position"
    const val SECONDS_PER_DAY = 24 * 60 * 60

    /** Below this a backwards clock is the unit correcting itself, not a new day. */
    const val ROLLOVER_SECONDS = 12 * 60 * 60

    /** The unit writes 0.0000 or -0.0000 for both degrees when it has no fix. */
    const val FIX_EPSILON = 1e-4

    /**
     * No column in the file states a unit, and the names are a fixed vocabulary rather than free
     * text, so these are stated once here.
     *
     * Every one is settled by the samples rather than assumed. Exhaust gas reaches 1,500 and
     * cylinder heads 500, which are only Fahrenheit numbers — a Celsius exhaust temperature runs
     * around 800. Outside air spans -40 to 26, which is only Celsius for the months these logs were
     * flown in. Anything not listed keeps an empty unit rather than a guess: density altitude and
     * fuel used are obvious, the battery ammeter and the discrete channels are not.
     */
    val UNITS: Map<String, String> = buildMap {
      for (i in 1..6) {
        put("E$i", "°F")
        put("C$i", "°F")
      }
      put("OILT", "°F")
      put("TIT", "°F")
      put("OILP", "PSI")
      put("RPM", "RPM")
      put("OAT", "°C")
      put("MAP", "inHg")
      put("FF", "gph")
      put("USED", "gal")
      put("AMP1", "amps")
      put("AMP2", "amps")
      put("AMPB", "amps")
      put("MBUS", "volts")
      put("EBUS", "volts")
      put("PALT", "ft")
      put("DALT", "ft")
    }

    /** `Avidyne Engine Data Log; DAU Software ID: 5.0` → `5.0`; the older units state none. */
    fun softwareVersion(title: String): String =
      title.substringAfter(SOFTWARE_ID_KEY, missingDelimiterValue = "")
        .trim()

    const val SOFTWARE_ID_KEY = "DAU Software ID:"

    /** `3/13/12 18:41:05` — month, day, two-digit year, then the clock. */
    fun parseHeaderStamp(line: String): HeaderStamp? {
      val text = line.trim()
      val space = text.indexOf(' ')
      if (space <= 0) return null
      val parts = text.substring(0, space)
        .split('/')
      if (parts.size != 3) return null
      val month = parts[0].toIntOrNull() ?: return null
      val day = parts[1].toIntOrNull() ?: return null
      val shortYear = parts[2].toIntOrNull() ?: return null
      if (month !in 1..12 || day !in 1..31 || shortYear !in 0..99) return null
      // These units shipped from the late nineties; a two-digit year rolls at the same point every
      // other reader of this format does.
      val year =
        if (shortYear >= NINETEEN_HUNDREDS_FROM) 1900 + shortYear else 2000 + shortYear
      val clock = parseClock(text, space + 1, text.length)
      if (clock < 0) return null
      return HeaderStamp(LocalDate(year, month, day), clock)
    }

    const val NINETEEN_HUNDREDS_FROM = 70

    /** The clock in column [column] of [line], or -1 when the row has none. */
    fun clockAt(line: String, column: Int): Int {
      var start = 0
      var col = 0
      while (col <= column) {
        var end = line.indexOf(',', start)
        if (end < 0) end = line.length
        if (col == column) {
          var s = start
          var e = end
          while (s < e && line[s] == ' ') s++
          while (e > s && line[e - 1] == ' ') e--
          return if (e > s) parseClock(line, s, e) else -1
        }
        if (end >= line.length) return -1
        start = end + 1
        col++
      }
      return -1
    }

    /** `18:41:06` to seconds of day, or -1. */
    fun parseClock(s: String, start: Int, end: Int): Int {
      if (end - start != 8) return -1
      if (s[start + 2] != ':' || s[start + 5] != ':') return -1
      val hour = parseIntAt(s, start, start + 2)
      val minute = parseIntAt(s, start + 3, start + 5)
      val second = parseIntAt(s, start + 6, start + 8)
      if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return -1
      return hour * 3600 + minute * 60 + second
    }
  }
}
