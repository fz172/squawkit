package dev.fanfly.wingslog.feature.datalog.datamanager.garmin

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParseException
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

/**
 * The fixture assertions were derived from the CSV with an independent script, not from this
 * parser, so a parser bug shows up as a mismatch rather than being pinned as the expected value.
 */
class GarminParserTest {

  private val parser = GarminParser()

  private suspend fun groundRun() =
    parser.parse(Fixtures.bytes(Fixtures.GROUND_RUN), Fixtures.GROUND_RUN)

  @Test
  fun readsTheHeaderVerbatim() = runTest {
    val parsed = groundRun()
    assertThat(parsed.format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_GARMIN_G3X)
    assertThat(parsed.parserVersion).isEqualTo(parser.version)
    with(parsed.source) {
      assertThat(product).isEqualTo("GDU 460")
      assertThat(unit).isEqualTo("PFD1")
      assertThat(software_version).isEqualTo("9.51")
      assertThat(system_id).isEqualTo("6000ABCD01234")
      assertThat(identity).isEqualTo("N1234X")
      assertThat(airframe_hours).isEqualTo("0.3")
      assertThat(engine_hours).isEqualTo("1.2")
    }
  }

  @Test
  fun timeBaseIsLocalWallClockPlusOffset() = runTest {
    val parsed = groundRun()
    // 14:47:56 at -07:00 is 21:47:56Z.
    assertThat(parsed.start).isEqualTo(Instant.parse("2026-09-02T21:47:56Z"))
    assertThat(parsed.utcOffsetMinutes).isEqualTo(-420)
    assertThat(parsed.sampleCount).isEqualTo(256)
    assertThat(parsed.durationSeconds).isEqualTo(255)
    assertThat(parsed.sampleRateHz).isEqualTo(1f)
    assertThat(parsed.data.timeSeconds.first()).isEqualTo(0)
    assertThat(parsed.data.timeSeconds.last()).isEqualTo(255)
  }

  @Test
  fun catalogueDropsTimeColumnsEmptyColumnsAndCollapsesPosition() = runTest {
    val parsed = groundRun()
    // 112 columns: 4 time columns, 34 entirely empty, latitude + longitude collapsed into one.
    assertThat(parsed.series).hasSize(73)
    val byKind = parsed.series.groupingBy { it.kind }
      .eachCount()
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC]).isEqualTo(
      55
    )
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE]).isEqualTo(
      7
    )
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT]).isEqualTo(10)
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION]).isEqualTo(
      1
    )
    assertThat(parsed.series.map { it.name }).doesNotContain("Height Above Ground")
    assertThat(parsed.series.map { it.name }).containsNoneOf(
      "Date",
      "Time",
      "UTC Time",
      "UTC Offset"
    )
  }

  @Test
  fun positionSitsWhereLatitudeWasAndKeepsEveryFix() = runTest {
    val parsed = groundRun()
    val position =
      parsed.series.single { it.kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION }
    assertThat(position.column).isEqualTo(4)
    assertThat(position.canonical_id).isEqualTo(CanonicalSeries.POSITION)
    assertThat(position.sample_count).isEqualTo(256)
    val data = parsed.data.position!!
    assertThat(data.latitude.first()).isWithin(1e-9)
      .of(39.0809735)
    assertThat(data.longitude.first()).isWithin(1e-9)
      .of(-114.1003992)
    assertThat(data.latitude.last()).isWithin(1e-9)
      .of(39.0810252)
    assertThat(data.longitude.last()).isWithin(1e-9)
      .of(-114.1003005)
    assertThat(parsed.data.numeric).doesNotContainKey(4)
    assertThat(parsed.data.numeric).doesNotContainKey(5)
  }

  @Test
  fun numericSeriesCarryUnitRangeAndCanonicalId() = runTest {
    val parsed = groundRun()
    val rpm = parsed.series.single { it.short_name == "E1 RPM" }
    assertThat(rpm.column).isEqualTo(81)
    assertThat(rpm.name).isEqualTo("RPM")
    assertThat(rpm.unit).isEmpty()
    assertThat(rpm.canonical_id).isEqualTo("engine[1].rpm")
    assertThat(rpm.min).isEqualTo(0.0)
    assertThat(rpm.max).isEqualTo(4250.0)
    assertThat(rpm.sample_count).isEqualTo(240)

    val oil = parsed.series.single { it.short_name == "E1 OilP" }
    assertThat(oil.name).isEqualTo("Oil Press")
    assertThat(oil.unit).isEqualTo("PSI")
    assertThat(oil.max).isEqualTo(58.0)
  }

  @Test
  fun cellsParseInPlaceWithNaNForEmptyAndForwardFillForDrawing() = runTest {
    val parsed = groundRun()
    val ias =
      parsed.data.numeric.getValue(parsed.series.single { it.short_name == "IAS" }.column)
    assertThat(ias.raw[0]).isEqualTo(15.3f)
    assertThat(ias.raw[10]).isEqualTo(16.2f)
    val rpm = parsed.data.numeric.getValue(81)
    assertThat(rpm.raw[0].isNaN()).isTrue()
    assertThat(rpm.raw[10].isNaN()).isTrue()
    assertThat(rpm.raw.last()).isEqualTo(1960f)
    assertThat(rpm.filled.last()).isEqualTo(1960f)
    // The first values are empty, so forward fill has nothing to carry yet.
    assertThat(rpm.filled[0].isNaN()).isTrue()
    val volts =
      parsed.data.numeric.getValue(parsed.series.single { it.short_name == "Volts1" }.column)
    assertThat(volts.raw.last()).isEqualTo(13.5f)
  }

  @Test
  fun textAndDiscreteColumnsAreTyped() = runTest {
    val parsed = groundRun()
    val fix = parsed.series.single { it.short_name == "GPSfix" }
    assertThat(fix.kind).isEqualTo(DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT)
    assertThat(parsed.data.text.getValue(fix.column)[0]).isEqualTo("3D-")
    val backup = parsed.series.single { it.name == "EFIS ON BKUP" }
    assertThat(backup.kind).isEqualTo(DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE)
    assertThat(backup.unit).isEqualTo("discrete")
    // COM2 mixes "118.350" with "118.350 RX": a column is numeric if any cell parses, and strict
    // parsing leaves the receiving rows empty rather than reading 118.35 out of them.
    val com2 = parsed.series.single { it.short_name == "COM2" }
    assertThat(com2.kind).isEqualTo(DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC)
    assertThat(com2.sample_count).isLessThan(parsed.sampleCount)
    assertThat(com2.sample_count).isGreaterThan(0)
  }

  @Test
  fun everySampleParsesAndIsAGroundRun() = runTest {
    Fixtures.sampleDir()
      .listFiles { f -> f.extension == "csv" }!!
      .forEach { file ->
        val parsed = parser.parse(file.readBytes(), file.name)
        assertThat(parsed.sampleCount).isGreaterThan(30)
        assertThat(parsed.series.size).isGreaterThan(60)
        assertThat(parsed.source.identity).isEqualTo("N1234X")
      }
  }

  @Test
  fun aClockStepKeepsRowOrderAndUsesTheMedianPeriod() = runTest {
    val rows = Fixtures.syntheticRows(6) { listOf("$it") }
      .toMutableList()
    // Row 3 jumps back two seconds, as a GPS time correction does.
    rows[3] = listOf("2026-09-02", "10:00:01", "-07:00", "3")
    val parsed = parser.parse(
      Fixtures.synthetic(
        listOf(Triple("RPM", "", "E1 RPM")),
        rows
      ), "x.csv"
    )
    assertThat(parsed.data.timeSeconds.toList()).containsExactly(
      0,
      1,
      2,
      3,
      4,
      5
    )
      .inOrder()
    assertThat(parsed.durationSeconds).isEqualTo(5)
  }

  @Test
  fun aFileThatIsNotGarminFails() = runTest {
    var threw = false
    try {
      parser.parse("a,b\n1,2\n".encodeToByteArray(), "x.csv")
    } catch (e: DataLogParseException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }
}
