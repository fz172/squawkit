package dev.fanfly.wingslog.feature.datalog.datamanager.garmin

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.datamanager.DerivedFields
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

/**
 * The G1000 half of the Garmin parser (design §6.2, T47).
 *
 * Like `GarminParserTest`, every expected value here was derived from the CSV with an independent
 * script rather than from the parser, so a parser bug reads as a mismatch instead of being pinned
 * as the answer.
 */
class GarminG1000ParserTest {

  private val parser = GarminParser()

  private suspend fun piston() =
    parser.parse(Fixtures.g1000Bytes(Fixtures.G1000_PISTON), Fixtures.G1000_PISTON)

  private suspend fun turbineStart() =
    parser.parse(Fixtures.g1000Bytes(Fixtures.G1000_TURBINE), Fixtures.G1000_TURBINE)

  private suspend fun turbineCruise() =
    parser.parse(Fixtures.g1000Bytes(Fixtures.G1000_CRUISE), Fixtures.G1000_CRUISE)

  @Test
  fun aUnitsRowMakesTheFileAG1000() = runTest {
    val parsed = piston()
    assertThat(parsed.format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_GARMIN_G1000)
    assertThat(parsed.parserVersion).isEqualTo(parser.version)
  }

  @Test
  fun theHeaderKeysThatExistAreReadAndTheRestStayEmpty() = runTest {
    with(piston().source) {
      assertThat(software_version).isEqualTo("14.03")
      assertThat(system_id).isEqualTo("000000000")
      // A G1000 header names the airframe, never the tail, so there is no identity to compare
      // against the Thing and no mismatch is possible from one of these files.
      assertThat(identity).isEmpty()
      assertThat(product).isEmpty()
    }
    assertThat(turbineStart().source.product).isEqualTo("GIFD")
  }

  @Test
  fun namesAndUnitsComeFromTwoDifferentRows() = runTest {
    val parsed = piston()
    // "psi" sits under "E1 OilP" in the units row; there is no long name to split a unit out of.
    val oil = parsed.series.single { it.short_name == "E1 OilP" }
    assertThat(oil.name).isEqualTo("E1 OilP")
    assertThat(oil.unit).isEqualTo("psi")
    assertThat(oil.canonical_id).isEqualTo("engine[1].oil_press")

    val cht = parsed.series.single { it.short_name == "E1 CHT1" }
    assertThat(cht.unit).isEqualTo("deg F")
    assertThat(cht.canonical_id).isEqualTo("engine[1].cht[1]")
    assertThat(cht.min).isWithin(1e-4)
      .of(48.26)
    assertThat(cht.max).isWithin(1e-4)
      .of(223.21)
  }

  @Test
  fun theShortNamesG1000SpellsItsOwnWayStillReachTheirCanonicalId() = runTest {
    val parsed = piston()
    fun canonicalOf(short: String) =
      parsed.series.single { it.short_name == short }.canonical_id

    assertThat(canonicalOf("AltB")).isEqualTo(CanonicalSeries.ALT_BARO)
    assertThat(canonicalOf("volt1")).isEqualTo(CanonicalSeries.volts(1))
    assertThat(canonicalOf("amp1")).isEqualTo(CanonicalSeries.amps(1))
    assertThat(canonicalOf("FQtyL")).isEqualTo(CanonicalSeries.fuelQty(1))
    assertThat(canonicalOf("FQtyR")).isEqualTo(CanonicalSeries.fuelQty(2))
    assertThat(canonicalOf("E1 TIT1")).isEqualTo("engine[1].tit[1]")
    assertThat(canonicalOf("IAS")).isEqualTo(CanonicalSeries.IAS)
    assertThat(canonicalOf("GndSpd")).isEqualTo(CanonicalSeries.GROUND_SPEED)
  }

  @Test
  fun turbineColumnsMapToTheirOwnEngineFields() = runTest {
    val parsed = turbineCruise()
    val itt = parsed.series.single { it.short_name == "E1 ITT" }
    assertThat(itt.canonical_id).isEqualTo("engine[1].itt")
    assertThat(itt.unit).isEqualTo("deg C")
    assertThat(itt.max).isWithin(1e-4)
      .of(760.38)
    assertThat(parsed.series.single { it.short_name == "E1 N1" }.canonical_id)
      .isEqualTo("engine[1].n1")
    assertThat(parsed.series.single { it.short_name == "E1 N2" }.canonical_id)
      .isEqualTo("engine[1].n2")
    // The second engine's columns are in the file but empty in this airframe, so they are dropped
    // rather than offered as flat lines.
    assertThat(parsed.series.map { it.short_name }).doesNotContain("E2 ITT")
  }

  @Test
  fun percentColumnsRecordedAsAFractionAreScaledToPercent() = runTest {
    // A G1000 writes 0.93 for 93% N1 under a units row that says `%`. Taken at face value the
    // viewer draws an engine at cruise as a flat line just above zero.
    val cruise = turbineCruise()
    val n1 = cruise.series.single { it.short_name == "E1 N1" }
    assertThat(n1.unit).isEqualTo("%")
    assertThat(n1.min).isWithin(1e-3)
      .of(93.0)
    assertThat(n1.max).isWithin(1e-3)
      .of(94.0)
    assertThat(cruise.data.numeric.getValue(n1.column).raw[0]).isWithin(1e-3f)
      .of(93.0f)
    assertThat(cruise.series.single { it.short_name == "E1 N2" }.max).isWithin(1e-3)
      .of(94.0)

    // Not only the spool speeds: engine power on the piston airframe is recorded the same way.
    val power = piston().series.single { it.short_name == "E1 %Pwr" }
    assertThat(power.max).isWithin(1e-3)
      .of(37.0)
  }

  @Test
  fun theTimeBaseReadsTheLocalClockAndItsOffset() = runTest {
    val parsed = piston()
    // 08:11:16 at -04:00 is 12:11:16Z.
    assertThat(parsed.start).isEqualTo(Instant.parse("2015-05-13T12:11:16Z"))
    assertThat(parsed.utcOffsetMinutes).isEqualTo(-240)
    assertThat(parsed.sampleCount).isEqualTo(240)
    assertThat(parsed.durationSeconds).isEqualTo(244)
    assertThat(parsed.sampleRateHz).isEqualTo(1f)
  }

  @Test
  fun rowsRecordedBeforeTheClockIsValidAreDatedBackwardsNotToEpochZero() = runTest {
    // The turbine log opens with four rows that carry no date, time or offset at all — the recorder
    // is running before its clock is. Left at zero they would date the whole log to 1970.
    val parsed = turbineStart()
    assertThat(parsed.start).isEqualTo(Instant.parse("2024-08-10T15:46:35Z"))
    assertThat(parsed.utcOffsetMinutes).isEqualTo(-300)
    assertThat(parsed.data.timeSeconds.first()).isEqualTo(0)
    assertThat(parsed.data.timeSeconds[4]).isEqualTo(4)
    assertThat(parsed.durationSeconds).isEqualTo(240)
  }

  @Test
  fun positionCollapsesAndKeepsOnlyTheRowsWithAFix() = runTest {
    val parsed = piston()
    val position =
      parsed.series.single { it.kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION }
    assertThat(position.column).isEqualTo(4)
    assertThat(position.canonical_id).isEqualTo(CanonicalSeries.POSITION)
    // The recorder has no fix for the first thirteen rows, so they stay out of the count.
    assertThat(position.sample_count).isEqualTo(227)
    val data = parsed.data.position!!
    assertThat(data.latitude[0].isNaN()).isTrue()
    assertThat(data.latitude.last()).isWithin(1e-7)
      .of(47.2282625)
    assertThat(data.longitude.last()).isWithin(1e-7)
      .of(-77.1244162)
    assertThat(parsed.data.numeric).doesNotContainKey(4)
    assertThat(parsed.data.numeric).doesNotContainKey(5)
  }

  @Test
  fun theCatalogueDropsTimeAndEmptyColumnsAndTypesTheRest() = runTest {
    val parsed = piston()
    // 71 columns: 3 time columns out, 12 entirely empty out, latitude and longitude in as one.
    assertThat(parsed.series).hasSize(55)
    val byKind = parsed.series.groupingBy { it.kind }
      .eachCount()
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC]).isEqualTo(49)
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE]).isEqualTo(1)
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT]).isEqualTo(4)
    assertThat(byKind[DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION]).isEqualTo(1)
    assertThat(parsed.series.map { it.short_name }).containsNoneOf(
      "Lcl Date",
      "Lcl Time",
      "UTCOfst"
    )
  }

  @Test
  fun aBoolUnitIsTheG1000SpellingOfDiscrete() = runTest {
    val parsed = piston()
    val afcs = parsed.series.single { it.short_name == "AfcsOn" }
    assertThat(afcs.unit).isEqualTo("bool")
    assertThat(afcs.kind).isEqualTo(DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE)
    // An enum column holds words, so it is text by the same rule that types every other column.
    val hsi = parsed.series.single { it.short_name == "HSIS" }
    assertThat(hsi.kind).isEqualTo(DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT)
    assertThat(parsed.data.text.getValue(hsi.column)[0]).isEqualTo("GPS")
  }

  @Test
  fun groundSpeedDecidesAirborneOnEitherFormat() = runTest {
    // The rule is written against a canonical id, so it reads a G1000 the same way it reads a G3X.
    assertThat(DerivedFields.airborne(piston())).isFalse()
    assertThat(DerivedFields.airborne(turbineStart())).isFalse()
    assertThat(DerivedFields.airborne(turbineCruise())).isTrue()
  }

  @Test
  fun everyG1000SampleParses() = runTest {
    Fixtures.g1000Dir()
      .listFiles { f -> f.extension == "csv" }!!
      .forEach { file ->
        val parsed = parser.parse(file.readBytes(), file.name)
        assertThat(parsed.format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_GARMIN_G1000)
        assertThat(parsed.sampleCount).isEqualTo(240)
        assertThat(parsed.series.size).isGreaterThan(40)
        assertThat(parsed.data.position).isNotNull()
      }
  }
}
