package dev.fanfly.wingslog.feature.datalog.datamanager.garmin

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
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.medianPositiveDelta
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseDoubleAt
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseIntAt
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.parseOffsetMinutes
import dev.fanfly.wingslog.feature.datalog.datamanager.csv.splitUnit
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.NumericColumn
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog
import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Garmin CSV, in both of the layouts Garmin ships (design §6.2). Line 1 is always
 * `#airframe_info`; what follows it is what differs:
 *
 * - **G3X** writes one line of long names carrying the unit in parentheses, then a line of short
 *   names: `Oil Press (PSI)` over `E1 OilP`.
 * - **G1000** writes a `#`-prefixed units line, then a line of short names indented with spaces:
 *   `psi` over `  E1 OilP`. There are no long names, so the short name is the display name too.
 *
 * Both reduce to the same three arrays — display name, short name, unit — before a single row walk,
 * and the short name is the key everything downstream reads because it is the vocabulary the two
 * formats share.
 *
 * The row walk indexes into each line and parses numbers in place rather than splitting cells into
 * strings — the difference between a phone parsing a 20,000-row file in under a second and in ten.
 * A [Breather] hands the thread back once a frame's worth of work has gone by, which is what keeps
 * the web build's one thread able to draw.
 */
class GarminParser : DataLogParser {

  override val formats: Set<DataLogFormat> = setOf(
    DataLogFormat.DATA_LOG_FORMAT_GARMIN_G3X,
    DataLogFormat.DATA_LOG_FORMAT_GARMIN_G1000,
  )

  /**
   * 1 G3X only. 2 added G1000. 3 scaled the G1000 percent columns.
   *
   * **Bump this in the same commit as any change to what `parse` emits** — a value, a unit, a name,
   * a canonical id, the set of columns. A stored record keeps the catalogue it was imported with and
   * `DataLogManagerImpl.load` rewrites it only when this number has moved, so a change that leaves
   * it alone reaches the charts and never reaches the sidebar. That is not a hypothetical: version 2
   * shipped the percent scaling without a bump, and every log imported in between kept reporting N1
   * as 0 to 1 beside a line drawn 0 to 100.
   */
  override val version: Int = 3

  override fun sniff(header: ByteArray): Confidence {
    val text = decodeText(header)
      .removePrefix(BOM)
    val first = text.substringBefore('\n')
      .trimEnd('\r')
    if (!first.startsWith(HEADER_PREFIX)) return Confidence.NONE
    // Either key alone settles it. `product="GDU` is a G3X display unit; `airframe_name=` is only
    // ever written by a G1000. A header with neither is still Garmin-shaped, so it stays POSSIBLE
    // rather than being handed to no one.
    val definite = first.contains(G3X_PRODUCT_KEY, ignoreCase = true) ||
      first.contains(G1000_AIRFRAME_KEY, ignoreCase = true)
    return if (definite) Confidence.DEFINITE else Confidence.POSSIBLE
  }

  /** A Garmin file is one recording, so this is always a list of one — or none, past its end. */
  override suspend fun parse(
    bytes: ByteArray,
    fileName: String,
    session: Int?,
  ): List<ParsedDataLog> {
    if (session != null && session != 0) return emptyList()
    return listOf(parseOne(bytes, fileName))
  }

  private suspend fun parseOne(
    bytes: ByteArray,
    fileName: String
  ): ParsedDataLog {
    val text = decodeText(bytes)
      .removePrefix(BOM)
    val lines = LineCursor(text)
    val header = lines.next()
      ?.takeIf { it.startsWith(HEADER_PREFIX) }
      ?: throw DataLogParseException("not a Garmin log: missing $HEADER_PREFIX header")
    val source = parseHeader(header)
    val namesOrUnitsLine =
      lines.next() ?: throw DataLogParseException("missing column names")
    val shortNamesLine =
      lines.next() ?: throw DataLogParseException("missing short names")
    val layout = Layout.of(namesOrUnitsLine, shortNamesLine)
    val columnCount = layout.names.size
    if (columnCount < 2) throw DataLogParseException("no columns")

    val dateCol = layout.columnOf("Lcl Date", "Date")
    val timeCol = layout.columnOf("Lcl Time", "Time")
    val utcTimeCol = layout.columnOf("UTC Time", "UTC Time")
    val utcOffsetCol = layout.columnOf("UTCOfst", "UTC Offset")
    if (dateCol < 0 || timeCol < 0) throw DataLogParseException("no Date/Time columns")
    val latCol = layout.columnOf("Latitude", "Latitude")
    val lonCol = layout.columnOf("Longitude", "Longitude")
    val hasPosition = latCol >= 0 && lonCol >= 0
    val skip = BooleanArray(columnCount).also {
      it[dateCol] = true; it[timeCol] = true
      if (utcTimeCol >= 0) it[utcTimeCol] = true
      if (utcOffsetCol >= 0) it[utcOffsetCol] = true
      if (hasPosition) {
        it[latCol] = true; it[lonCol] = true
      }
    }

    val capacity = lines.remainingLineCount()
    val columns = arrayOfNulls<ColumnAccumulator>(columnCount)
    val epochSeconds = LongArray(capacity)
    val latitude =
      if (hasPosition) DoubleArray(capacity) { Double.NaN } else null
    val longitude =
      if (hasPosition) DoubleArray(capacity) { Double.NaN } else null
    var utcOffsetMinutes = 0
    var offsetSeen = false
    var rows = 0
    var firstClockedRow = -1
    val breather = Breather()
    var year = 0;
    var month = 0;
    var day = 0
    var hour = 0;
    var minute = 0;
    var second = 0

    while (true) {
      val line = lines.next() ?: break
      if (line.isEmpty()) continue
      var start = 0
      var col = 0
      var dateOk = false
      var timeOk = false
      while (col < columnCount && start <= line.length) {
        var end = line.indexOf(',', start)
        if (end < 0) end = line.length
        var s = start
        var e = end
        while (s < e && line[s] == ' ') s++
        while (e > s && line[e - 1] == ' ') e--
        if (e > s) {
          when {
            col == dateCol -> if (e - s == 10) {
              year = parseIntAt(line, s, s + 4); month =
                parseIntAt(line, s + 5, s + 7)
              day = parseIntAt(line, s + 8, s + 10); dateOk =
                year > 0 && month in 1..12 && day in 1..31
            }

            col == timeCol -> if (e - s == 8) {
              hour = parseIntAt(line, s, s + 2); minute =
                parseIntAt(line, s + 3, s + 5)
              second = parseIntAt(line, s + 6, s + 8); timeOk =
                hour in 0..23 && minute in 0..59 && second in 0..60
            }

            col == utcOffsetCol -> if (!offsetSeen) {
              utcOffsetMinutes = parseOffsetMinutes(line, s, e); offsetSeen =
                true
            }

            col == utcTimeCol -> Unit
            hasPosition && col == latCol -> latitude!![rows] =
              parseDoubleAt(line, s, e)

            hasPosition && col == lonCol -> longitude!![rows] =
              parseDoubleAt(line, s, e)

            else -> {
              val acc = columns[col]
                ?: ColumnAccumulator(capacity).also { columns[col] = it }
              val v = parseDoubleAt(line, s, e)
              if (v.isNaN()) acc.text(
                rows,
                line.substring(s, e)
              ) else acc.number(rows, v.toFloat())
            }
          }
        }
        start = end + 1
        col++
      }
      epochSeconds[rows] = if (dateOk && timeOk) {
        if (firstClockedRow < 0) firstClockedRow = rows
        LocalDateTime(
          year,
          month,
          day,
          hour,
          minute,
          second
        ).toInstant(TimeZone.UTC).epochSeconds -
          utcOffsetMinutes * 60L
      } else if (rows > 0) epochSeconds[rows - 1] else 0L
      rows++
      breather.breathe()
    }
    if (rows == 0) throw DataLogParseException("no rows")

    val medianPeriod = medianPositiveDelta(epochSeconds, rows)
    // A G1000 starts recording before its clock is valid, so the first rows carry no date or time
    // at all. Left alone they would sit at epoch zero and the log would be dated 1970; extrapolated
    // backwards at the median period they land just before the first real reading, which is where
    // they were recorded.
    for (i in 0 until firstClockedRow.coerceAtLeast(0)) {
      epochSeconds[i] =
        epochSeconds[firstClockedRow] - (firstClockedRow - i).toLong() * medianPeriod
    }

    val timeSeconds = IntArray(rows)
    for (i in 1 until rows) {
      // Anchored on the clock so a GPS time step nets out once the clock catches up; a row whose
      // clock stands still or runs backwards advances by the median period instead.
      val fromClock =
        (epochSeconds[i] - epochSeconds[0]).coerceIn(0L, Int.MAX_VALUE.toLong())
          .toInt()
      timeSeconds[i] = maxOf(timeSeconds[i - 1] + medianPeriod, fromClock)
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
      val name = layout.names[col]
      val unit = layout.units[col]
      val shortName = layout.shortNames.getOrElse(col) { "" }
      if (acc.numericCount > 0) {
        val raw = acc.floats!!.copyOf(rows)
        val scale = layout.percentScale(unit, acc.min, acc.max)
        if (scale != 1f) for (i in raw.indices) raw[i] *= scale
        numeric[col] = NumericColumn(raw, forwardFilled(raw))
        val kind =
          if (unit in DISCRETE_UNITS) DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE
          else DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC
        series += DataLogSeries(
          column = col,
          name = name,
          short_name = shortName,
          unit = unit,
          kind = kind,
          canonical_id = CanonicalSeriesRegistry.canonicalIdFor(shortName),
          min = (acc.min * scale).toDouble(),
          max = (acc.max * scale).toDouble(),
          sample_count = acc.numericCount,
        )
      } else {
        texts[col] = acc.texts!!.copyOf(rows)
        series += DataLogSeries(
          column = col,
          name = name,
          short_name = shortName,
          unit = unit,
          kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT,
          sample_count = acc.textCount,
        )
      }
    }

    return ParsedDataLog(
      format = layout.format,
      parserVersion = version,
      source = source,
      start = Instant.fromEpochSeconds(epochSeconds[0]),
      utcOffsetMinutes = utcOffsetMinutes,
      durationSeconds = timeSeconds[rows - 1],
      sampleCount = rows,
      sampleRateHz = 1f / medianPeriod,
      series = series,
      data = DataLogSeriesData(timeSeconds, numeric, texts, position),
    )
  }

  /**
   * `key="value"` pairs into [DataLogSource]. Keys are lower-cased because a G1000 writes `Product`
   * where a G3X writes `product`, and spells its software version `unit_software_version`.
   *
   * A G1000 also carries `airframe_name` ("Cirrus SR22 Turbo"), which has no field here and is
   * dropped: it describes the aircraft rather than the recorder, and nothing renders it. It is the
   * one thing to add if a surface ever wants to name the airframe.
   */
  private fun parseHeader(header: String): DataLogSource {
    val pairs = HashMap<String, String>()
    header.removePrefix(HEADER_PREFIX)
      .split(',')
      .forEach { pair ->
        val eq = pair.indexOf('=')
        if (eq > 0) pairs[pair.substring(0, eq)
          .trim()
          .lowercase()] = pair.substring(eq + 1)
          .trim()
          .trim('"')
      }
    return DataLogSource(
      product = pairs["product"].orEmpty(),
      unit = pairs["unit"].orEmpty(),
      software_version = pairs["software_version"]
        ?: pairs["unit_software_version"].orEmpty(),
      system_id = pairs["system_id"].orEmpty(),
      identity = pairs["aircraft_ident"].orEmpty(),
      airframe_hours = pairs["airframe_hours"].orEmpty(),
      engine_hours = pairs["engine_hours"].orEmpty(),
    )
  }

  /**
   * The two header lines after `#airframe_info`, reduced to the three arrays the rest of the parse
   * reads. Which format a file is falls out of one character: a `#` on the second line means the
   * units are on their own row, which only a G1000 writes.
   */
  private class Layout(
    val format: DataLogFormat,
    val names: List<String>,
    val shortNames: List<String>,
    val units: List<String>,
  ) {

    /**
     * 100 for a G1000 column that labels itself `%` but records a fraction of one, 1 otherwise.
     *
     * A G1000 writes `0.93` for 93% N1 and `1.14` for 114% power, under a units row that says `%`
     * in both cases — the file disagrees with itself, and taken at face value the viewer draws an
     * engine at cruise as a flat line near zero. A G3X does not do this: its percent columns reach
     * 43 and 345, so the correction is scoped to the format rather than guessed from the numbers.
     *
     * The observed range is still a guard rather than the rule. It only ever prevents scaling, so a
     * G1000 variant that one day records true percentages is left alone instead of multiplied to a
     * hundred times its real reading.
     */
    fun percentScale(unit: String, min: Float, max: Float): Float {
      if (format != DataLogFormat.DATA_LOG_FORMAT_GARMIN_G1000 || unit != PERCENT_UNIT) return 1f
      val largest =
        maxOf(if (min.isFinite()) -min else 0f, if (max.isFinite()) max else 0f)
      return if (largest <= PERCENT_FRACTION_CEILING) 100f else 1f
    }

    /**
     * The column playing a given role, by its short name first. The short names are the vocabulary
     * the two formats share, so [longName] is only the fallback for a G3X file whose short-name row
     * leaves the cell blank — which it does for several columns.
     */
    fun columnOf(shortName: String, longName: String): Int {
      val byShort = shortNames.indexOfFirst { it == shortName }
      if (byShort >= 0) return byShort
      return names.indexOfFirst { it.startsWith(longName) }
    }

    companion object {

      fun of(namesOrUnitsLine: String, shortNamesLine: String): Layout {
        val shortNames = shortNamesLine.split(',')
          .map { it.trim() }
        if (!namesOrUnitsLine.startsWith(UNITS_ROW_PREFIX)) {
          // G3X: long names carry their unit in parentheses, and there is no units row.
          val longNames = namesOrUnitsLine.split(',')
            .map { it.trim() }
          val split = longNames.map(::splitUnit)
          return Layout(
            format = DataLogFormat.DATA_LOG_FORMAT_GARMIN_G3X,
            names = split.map { it.first },
            shortNames = shortNames,
            units = split.map { it.second },
          )
        }
        // G1000: the short name is the only name there is, so it is also the display name.
        val units = namesOrUnitsLine.removePrefix(UNITS_ROW_PREFIX)
          .split(',')
          .map { it.trim() }
        return Layout(
          format = DataLogFormat.DATA_LOG_FORMAT_GARMIN_G1000,
          names = shortNames,
          shortNames = shortNames,
          units = shortNames.indices.map { units.getOrElse(it) { "" } },
        )
      }

    }
  }

  private companion object {
    const val HEADER_PREFIX = "#airframe_info,"
    const val BOM = "\uFEFF"

    /** Only a G1000 puts the units on their own line, and it marks that line with a `#`. */
    const val UNITS_ROW_PREFIX = "#"
    const val G3X_PRODUCT_KEY = "product=\"GDU"
    const val G1000_AIRFRAME_KEY = "airframe_name="

    /** G3X spells an on/off column `discrete`; a G1000 spells the same thing `bool`. */
    val DISCRETE_UNITS = setOf("discrete", "bool")
    const val PERCENT_UNIT = "%"

    /** Above this a G1000 percent column is already a percentage, so it is left alone. */
    const val PERCENT_FRACTION_CEILING = 1.5f
    const val POSITION_NAME = "Position"
  }
}
