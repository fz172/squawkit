package dev.fanfly.wingslog.feature.datalog.datamanager.dynon

import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.datalog.DataLogSource
import dev.fanfly.wingslog.feature.datalog.datamanager.CanonicalSeriesRegistry
import dev.fanfly.wingslog.feature.datalog.datamanager.Confidence
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParseException
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParser
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.Breather
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.ColumnAccumulator
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.LineCursor
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.decodeText
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.forwardFilled
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseDoubleAt
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseIntAt
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.splitUnit
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.NumericColumn
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog
import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Dynon SkyView `USER_LOG_DATA` CSV (design §6.2).
 *
 * Simpler than a Garmin on the surface and harder underneath. There is no metadata header at all:
 * one row of long names carrying their unit in parentheses, then the samples. Everything a Garmin
 * states about itself — the recorder, its software, the aeroplane — a SkyView leaves to the file
 * name, which its own exporter writes as
 * `<date>-<tail>-SN<serial>-<firmware>-USER_LOG_DATA.csv`.
 *
 * **One file is many recordings.** `Session Time` counts seconds since the unit powered on and
 * resets at every power cycle, and a download holds every session since the last one — twenty-one
 * of them, across a month, in the file this parser was written against. Each becomes its own
 * [ParsedDataLog]. Merging them would put a fortnight of empty space in the middle of a chart.
 *
 * **Half of those sessions have no date.** A SkyView that never gets a GPS fix writes
 * `UNKNOWN_DATE_TIME`, leaving a time of day from its own clock and nothing that says which day. The
 * date then comes from the file name and [ParsedDataLog.startApproximate] says so, because a
 * session with no date at all is worse than one whose date is marked as inferred.
 */
class DynonParser : DataLogParser {

  override val formats: Set<DataLogFormat> =
    setOf(DataLogFormat.DATA_LOG_FORMAT_DYNON_SKYVIEW)

  /**
   * 1 the first SkyView release. 2 labelled `Percent Power` as a percentage.
   *
   * **Bump this in the same commit as any change to what `parse` emits** — a value, a unit, a name,
   * a canonical id, the set of columns. A stored record keeps the catalogue it was imported with
   * and `DataLogManagerImpl.load` rewrites it only when this number has moved, so a change that
   * leaves it alone reaches the charts and never reaches the sidebar.
   */
  override val version: Int = 2

  override fun sniff(header: ByteArray): Confidence {
    val text = decodeText(header)
      .removePrefix(BOM)
    val first = text.substringBefore('\n')
      .trimEnd('\r')
    if (!first.startsWith(SESSION_TIME_COLUMN)) return Confidence.NONE
    // The first column alone is suggestive; a second Dynon column name settles it, and costs
    // nothing because the whole header row is inside the sniff window.
    val definite = SNIFF_COLUMNS.any { first.contains(it) }
    return if (definite) Confidence.DEFINITE else Confidence.POSSIBLE
  }

  override suspend fun parse(
    bytes: ByteArray,
    fileName: String,
    session: Int?,
  ): List<ParsedDataLog> {
    val text = decodeText(bytes)
      .removePrefix(BOM)
    val header = LineCursor(text)
    val headerLine = header.next()
      ?: throw DataLogParseException("not a Dynon log: empty file")
    if (!headerLine.startsWith(SESSION_TIME_COLUMN)) {
      throw DataLogParseException("not a Dynon log: no $SESSION_TIME_COLUMN column")
    }
    val layout = Layout.of(headerLine)
    val bodyStart = header.position

    val bounds = sessionBounds(text, bodyStart, layout)
    if (bounds.isEmpty()) throw DataLogParseException("no rows")
    val wanted = when (session) {
      null -> bounds.indices
      in bounds.indices -> session..session
      else -> return emptyList()
    }

    val source = sourceFromFileName(fileName)
    val fallbackDate = dateFromFileName(fileName)
    return wanted.map { index ->
      buildSession(text, layout, bounds[index], source, fallbackDate)
    }
  }

  /**
   * Row ranges, one per power cycle, found by reading nothing but the first cell of each line.
   *
   * A separate pass so the build below can allocate each session's arrays at exactly its row count.
   * Growing them instead would mean copying a 20,000-row column every time it doubled, on a file
   * that already costs a full scan to read.
   */
  private suspend fun sessionBounds(
    text: String,
    bodyStart: Int,
    layout: Layout,
  ): List<Bounds> {
    val bounds = ArrayList<Bounds>()
    val breather = Breather()
    val lines = LineCursor(text, bodyStart)
    var offset = lines.position
    var start = -1
    var startOffset = 0
    var rows = 0
    var previous = Double.NaN
    while (true) {
      val lineStart = offset
      val line = lines.next() ?: break
      offset = lines.position
      val elapsed = firstCell(line, layout.sessionTimeColumn)
      if (elapsed.isNaN()) continue
      if (start < 0 || elapsed < previous) {
        if (start >= 0) bounds += Bounds(startOffset, rows)
        start = 0
        startOffset = lineStart
        rows = 0
      }
      previous = elapsed
      rows++
      // The web build has one thread, so a full scan of a 46 MB download without this is a freeze
      // the spinner never gets to paint through. Everywhere else this costs nothing.
      breather.breathe()
    }
    if (start >= 0) bounds += Bounds(startOffset, rows)
    return bounds
  }

  private fun firstCell(line: String, column: Int): Double {
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
        return if (e > s) parseDoubleAt(line, s, e) else Double.NaN
      }
      if (end >= line.length) return Double.NaN
      start = end + 1
      col++
    }
    return Double.NaN
  }

  private suspend fun buildSession(
    text: String,
    layout: Layout,
    bounds: Bounds,
    source: DataLogSource,
    fallbackDate: LocalDateTime?,
  ): ParsedDataLog {
    val columnCount = layout.names.size
    val capacity = bounds.rows
    val columns = arrayOfNulls<ColumnAccumulator>(columnCount)
    val elapsed = DoubleArray(capacity)
    val hasPosition = layout.latitudeColumn >= 0 && layout.longitudeColumn >= 0
    val latitude =
      if (hasPosition) DoubleArray(capacity) { Double.NaN } else null
    val longitude =
      if (hasPosition) DoubleArray(capacity) { Double.NaN } else null
    var rows = 0
    var gpsClock: LocalDateTime? = null
    var gpsClockRow = -1
    var systemClockSeconds = -1
    var systemClockRow = -1
    var gpsClockSeconds = -1
    val breather = Breather()

    val lines = LineCursor(text, bounds.offset)
    while (rows < capacity) {
      val line = lines.next() ?: break
      if (line.isEmpty()) continue
      var start = 0
      var col = 0
      while (col < columnCount && start <= line.length) {
        var end = line.indexOf(',', start)
        if (end < 0) end = line.length
        var s = start
        var e = end
        while (s < e && line[s] == ' ') s++
        while (e > s && line[e - 1] == ' ') e--
        if (e > s) {
          when (col) {
            layout.sessionTimeColumn -> elapsed[rows] =
              parseDoubleAt(line, s, e)

            layout.gpsDateTimeColumn -> if (gpsClock == null) {
              parseGpsDateTime(line, s, e)?.let {
                gpsClock = it
                gpsClockRow = rows
                gpsClockSeconds = it.time.toSecondOfDay()
              }
            }

            layout.systemTimeColumn -> if (systemClockRow < 0) {
              val seconds = parseClock(line, s, e)
              if (seconds >= 0) {
                systemClockSeconds = seconds
                systemClockRow = rows
              }
            }

            layout.latitudeColumn -> if (hasPosition) {
              latitude!![rows] = parseDoubleAt(line, s, e)
            }

            layout.longitudeColumn -> if (hasPosition) {
              longitude!![rows] = parseDoubleAt(line, s, e)
            }

            else -> {
              val acc = columns[col]
                ?: ColumnAccumulator(capacity).also { columns[col] = it }
              val v = parseDoubleAt(line, s, e)
              if (v.isNaN()) acc.text(rows, line.substring(s, e))
              else acc.number(rows, v.toFloat())
            }
          }
        }
        start = end + 1
        col++
      }
      rows++
      breather.breathe()
    }
    if (rows == 0) throw DataLogParseException("no rows")

    // Session Time is already elapsed seconds, and it is the recorder's own monotonic clock rather
    // than a wall clock, so it needs none of the step-and-rewind handling a Garmin's date and time
    // columns do.
    val base = elapsed[0]
    val timeSeconds = IntArray(rows) {
      (elapsed[it] - base).roundToInt()
        .coerceAtLeast(0)
    }
    for (i in 1 until rows) {
      if (timeSeconds[i] < timeSeconds[i - 1]) timeSeconds[i] =
        timeSeconds[i - 1]
    }
    val span = elapsed[rows - 1] - base
    val sampleRateHz =
      if (span > 0.0) ((rows - 1) / span).toFloat() else 1f

    // The two clocks differ by the unit's UTC offset: the GPS one is UTC, the unit's own is local.
    val offsetMinutes =
      if (gpsClockSeconds >= 0 && systemClockSeconds >= 0 && gpsClockRow == systemClockRow) {
        offsetMinutesBetween(systemClockSeconds, gpsClockSeconds)
      } else {
        0
      }

    val recorded = gpsClock
    val start = if (recorded != null) {
      // Back to row 0 from whichever row first had a fix.
      Instant.fromEpochSeconds(
        recorded.toInstant(TimeZone.UTC).epochSeconds - timeSeconds[gpsClockRow]
      )
    } else {
      val date = fallbackDate?.date
      val secondOfDay = if (systemClockSeconds >= 0) systemClockSeconds else 0
      if (date == null) {
        Instant.fromEpochSeconds(0)
      } else {
        Instant.fromEpochSeconds(
          LocalDateTime(
            date,
            kotlinx.datetime.LocalTime.fromSecondOfDay(secondOfDay)
          )
            .toInstant(TimeZone.UTC).epochSeconds - offsetMinutes * 60L -
            (if (systemClockRow > 0) timeSeconds[systemClockRow].toLong() else 0L)
        )
      }
    }

    val series = ArrayList<DataLogSeries>(columnCount)
    val numeric = LinkedHashMap<Int, NumericColumn>()
    val texts = LinkedHashMap<Int, Array<String?>>()
    var position: PositionColumn? = null
    for (col in 0 until columnCount) {
      if (hasPosition && col == layout.latitudeColumn) {
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
      if (layout.skip[col]) continue
      val acc = columns[col] ?: continue
      val name = layout.names[col]
      val unit = layout.units[col].ifEmpty { IMPLIED_UNITS[name].orEmpty() }
      if (acc.numericCount > 0) {
        val raw = acc.floats!!.copyOf(rows)
        numeric[col] = NumericColumn(raw, forwardFilled(raw))
        val kind =
          if (unit in DISCRETE_UNITS) DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE
          else DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC
        series += DataLogSeries(
          column = col,
          name = name,
          short_name = name,
          unit = unit,
          kind = kind,
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
      format = DataLogFormat.DATA_LOG_FORMAT_DYNON_SKYVIEW,
      parserVersion = version,
      source = source,
      start = start,
      startApproximate = recorded == null,
      utcOffsetMinutes = offsetMinutes,
      durationSeconds = timeSeconds[rows - 1],
      sampleCount = rows,
      sampleRateHz = sampleRateHz,
      series = series,
      data = DataLogSeriesData(timeSeconds, numeric, texts, position),
    )
  }

  /** The column names, their units, and which columns the walk above reads for something else. */
  private class Layout(
    val names: List<String>,
    val units: List<String>,
    val sessionTimeColumn: Int,
    val gpsDateTimeColumn: Int,
    val systemTimeColumn: Int,
    val latitudeColumn: Int,
    val longitudeColumn: Int,
  ) {
    val skip: BooleanArray = BooleanArray(names.size).also {
      for (c in listOf(
        sessionTimeColumn,
        gpsDateTimeColumn,
        systemTimeColumn
      )) {
        if (c in names.indices) it[c] = true
      }
      if (longitudeColumn in names.indices) it[longitudeColumn] = true
    }

    companion object {
      fun of(headerLine: String): Layout {
        // The exporter ends the header with a comma, so the split leaves a nameless last column
        // that never carries a value.
        val raw = headerLine.split(',')
          .map { it.trim() }
        val cells = if (raw.isNotEmpty() && raw.last()
            .isEmpty()
        ) raw.dropLast(1) else raw
        if (cells.size < 2) throw DataLogParseException("no columns")
        val split = cells.map(::splitUnit)
        val names = split.map { it.first }
        return Layout(
          names = names,
          units = split.map { it.second },
          sessionTimeColumn = names.indexOf(SESSION_TIME_NAME),
          gpsDateTimeColumn = names.indexOf(GPS_DATE_TIME_NAME),
          systemTimeColumn = names.indexOf(SYSTEM_TIME_NAME),
          latitudeColumn = names.indexOf(LATITUDE_NAME),
          longitudeColumn = names.indexOf(LONGITUDE_NAME),
        )
      }
    }
  }

  private class Bounds(val offset: Int, val rows: Int)

  private companion object {
    const val BOM = "﻿"
    const val SESSION_TIME_NAME = "Session Time"
    const val SESSION_TIME_COLUMN = "$SESSION_TIME_NAME,"
    const val GPS_DATE_TIME_NAME = "GPS Date & Time"
    const val SYSTEM_TIME_NAME = "System Time"
    const val LATITUDE_NAME = "Latitude"
    const val LONGITUDE_NAME = "Longitude"
    const val POSITION_NAME = "Position"

    /** A SkyView marks an on/off channel `bool`; nothing else in the file uses that unit. */
    val DISCRETE_UNITS = setOf("bool")

    /**
     * Units a SkyView states in the column name instead of in parentheses.
     *
     * `Percent Power` is already a percentage — 0 to 106 over a flight — and the header simply
     * never says so, which left the sidebar showing a bare range. This is a label and nothing more:
     * no value is touched, unlike the G1000's percent columns, which really do hold a fraction.
     */
    val IMPLIED_UNITS = mapOf("Percent Power" to "%")

    /** Any one of these beside `Session Time` is a SkyView and nothing else. */
    val SNIFF_COLUMNS =
      listOf("GPS Fix Quality", "Thermocouple 1", "EGT Leaning State")

    /** `2019-03-30 12:28:44` in UTC; `UNKNOWN_DATE_TIME` and a truncated row give null. */
    fun parseGpsDateTime(s: String, start: Int, end: Int): LocalDateTime? {
      if (end - start != 19) return null
      val year = parseIntAt(s, start, start + 4)
      val month = parseIntAt(s, start + 5, start + 7)
      val day = parseIntAt(s, start + 8, start + 10)
      val hour = parseIntAt(s, start + 11, start + 13)
      val minute = parseIntAt(s, start + 14, start + 16)
      val second = parseIntAt(s, start + 17, start + 19)
      if (year <= 0 || month !in 1..12 || day !in 1..31) return null
      if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null
      return LocalDateTime(
        year,
        month,
        day,
        hour,
        minute,
        second.coerceAtMost(59)
      )
    }

    /** `12:28:43` to seconds of day, or -1. */
    fun parseClock(s: String, start: Int, end: Int): Int {
      if (end - start != 8) return -1
      val hour = parseIntAt(s, start, start + 2)
      val minute = parseIntAt(s, start + 3, start + 5)
      val second = parseIntAt(s, start + 6, start + 8)
      if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return -1
      return hour * 3600 + minute * 60 + second
    }

    /**
     * Local minus UTC, to the nearest quarter hour.
     *
     * Rounded because the two clocks are read from the same row but tick independently, so they can
     * disagree by a second; every real offset is a multiple of fifteen minutes, so rounding to one
     * turns that jitter into the answer rather than into a minute of error.
     */
    fun offsetMinutesBetween(localSecondOfDay: Int, utcSecondOfDay: Int): Int {
      var difference = (localSecondOfDay - utcSecondOfDay) / 60
      if (difference > 12 * 60) difference -= 24 * 60
      if (difference < -12 * 60) difference += 24 * 60
      val quarters = (difference.toDouble() / 15.0).roundToInt()
      return quarters * 15
    }

    /**
     * `2019-04-28-N803DR-SN6049-15_3_4_4867-USER_LOG_DATA.csv` — what a SkyView's own exporter
     * writes, and the only place one states its tail number, its serial or its firmware. A file
     * renamed by hand simply yields nothing.
     */
    val FILE_NAME = Regex(
      """^(\d{4}-\d{2}-\d{2})-([A-Za-z0-9]+)-SN(\w+)-([\w.]+)-USER_LOG_DATA\.csv$""",
      RegexOption.IGNORE_CASE,
    )

    fun sourceFromFileName(fileName: String): DataLogSource {
      val m = FILE_NAME.matchEntire(fileName.trim()) ?: return DataLogSource(
        product = PRODUCT
      )
      return DataLogSource(
        product = PRODUCT,
        identity = m.groupValues[2],
        system_id = "SN${m.groupValues[3]}",
        software_version = m.groupValues[4].replace('_', '.'),
      )
    }

    fun dateFromFileName(fileName: String): LocalDateTime? {
      val stamp = FILE_NAME.matchEntire(fileName.trim())?.groupValues?.get(1)
        ?: return null
      val year = stamp.substring(0, 4)
        .toIntOrNull() ?: return null
      val month = stamp.substring(5, 7)
        .toIntOrNull() ?: return null
      val day = stamp.substring(8, 10)
        .toIntOrNull() ?: return null
      if (month !in 1..12 || day !in 1..31) return null
      return LocalDateTime(year, month, day, 0, 0)
    }

    const val PRODUCT = "Dynon SkyView"
  }
}
