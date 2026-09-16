package dev.fanfly.wingslog.feature.datalog.datamanager.dynon

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.datamanager.Confidence
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParseException
import dev.fanfly.wingslog.feature.datalog.datamanager.DerivedFields
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

/**
 * The Dynon SkyView parser (design §6.2, T48).
 *
 * As with the Garmin tests, every expected value was derived from the CSV with an independent
 * script rather than from the parser, so a parser bug reads as a mismatch instead of being pinned
 * as the answer.
 */
class DynonParserTest {

  private val parser = DynonParser()

  private suspend fun single() =
    parser.parse(
      Fixtures.dynonBytes(Fixtures.DYNON_SINGLE),
      Fixtures.DYNON_SINGLE
    )

  private suspend fun download() =
    parser.parse(
      Fixtures.dynonBytes(Fixtures.DYNON_SESSIONS),
      Fixtures.DYNON_SESSIONS
    )

  @Test
  fun aSessionTimeHeaderIsASkyView() {
    fun sniff(text: String) = parser.sniff(text.encodeToByteArray())

    assertThat(sniff("Session Time,GPS Fix Quality,Latitude (deg)\n0.25,2,+38.7\n"))
      .isEqualTo(Confidence.DEFINITE)
    // The first column alone is suggestive but not proof, so it goes to this parser and fails in
    // parse() if the body turns out to be something else.
    assertThat(sniff("Session Time,Something Else\n0.25,1\n")).isEqualTo(
      Confidence.POSSIBLE
    )
    assertThat(sniff("#airframe_info,product=\"GDU 460\"\n")).isEqualTo(
      Confidence.NONE
    )
    assertThat(sniff("")).isEqualTo(Confidence.NONE)
    assertThat(
      parser.sniff(
        Fixtures.dynonBytes(Fixtures.DYNON_SESSIONS)
          .copyOf(4096)
      )
    ).isEqualTo(Confidence.DEFINITE)
  }

  @Test
  fun oneFileIsOneRecordingWhenTheUnitNeverRestarted() = runTest {
    val sessions = single()
    assertThat(sessions).hasSize(1)
    val parsed = sessions.single()
    assertThat(parsed.format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_DYNON_SKYVIEW)
    assertThat(parsed.sampleCount).isEqualTo(400)
    assertThat(parsed.durationSeconds).isEqualTo(100)
    // Four samples a second, which is the rate this unit was configured at.
    assertThat(parsed.sampleRateHz).isWithin(1e-3f)
      .of(4f)
    assertThat(parsed.start).isEqualTo(Instant.parse("2018-07-27T08:29:59Z"))
    assertThat(parsed.startApproximate).isFalse()
  }

  @Test
  fun aPowerCycleStartsANewRecordingRatherThanAGapInTheOldOne() = runTest {
    // Session Time resets to zero at each power-on, and a download holds every session since the
    // last one. Merged they would be one chart with a month of empty space across the middle.
    val sessions = download()
    assertThat(sessions).hasSize(5)
    assertThat(sessions.map { it.sampleCount }).containsExactly(
      19,
      300,
      364,
      300,
      300
    )
      .inOrder()
    // The first session spans 4.5 seconds and rounds up, the way a half always does here; the
    // reference script that produced these numbers rounds halves to even and said 4.
    assertThat(sessions.map { it.durationSeconds }).containsExactly(
      5,
      75,
      91,
      75,
      75
    )
      .inOrder()
    assertThat(sessions[0].start).isEqualTo(Instant.parse("2019-03-30T12:28:44Z"))
    assertThat(sessions[1].start).isEqualTo(Instant.parse("2019-03-30T20:15:13Z"))
    assertThat(sessions[2].start).isEqualTo(Instant.parse("2019-04-06T21:35:52Z"))
  }

  @Test
  fun aSessionThatNeverGotAFixTakesItsDateFromTheFileName() = runTest {
    // The recorder writes UNKNOWN_DATE_TIME and its own clock, which gives a time of day and
    // nothing that says which day. The file name carries the download date.
    val sessions = download()
    val undated = sessions[3]
    assertThat(undated.startApproximate).isTrue()
    assertThat(undated.start).isEqualTo(Instant.parse("2019-04-28T17:04:02Z"))
    assertThat(sessions[4].startApproximate).isTrue()
    // And the ones that did get a fix are not marked, so the flag means what it says.
    assertThat(
      sessions.take(3)
        .map { it.startApproximate })
      .containsExactly(false, false, false)
  }

  @Test
  fun onlyTheRequestedSessionIsBuilt() = runTest {
    // What the viewer asks for: one record out of a file holding many, without paying to build the
    // other twenty.
    val fourth = parser.parse(
      Fixtures.dynonBytes(Fixtures.DYNON_SESSIONS),
      Fixtures.DYNON_SESSIONS,
      session = 3,
    )
    assertThat(fourth).hasSize(1)
    assertThat(fourth.single().sampleCount).isEqualTo(300)
    assertThat(fourth.single().startApproximate).isTrue()

    assertThat(
      parser.parse(
        Fixtures.dynonBytes(Fixtures.DYNON_SESSIONS),
        Fixtures.DYNON_SESSIONS,
        session = 99,
      )
    ).isEmpty()
  }

  @Test
  fun theFileNameIsTheOnlyPlaceASkyViewNamesItself() = runTest {
    // There is no metadata header in the format at all, so the exporter's own file name is where
    // the tail number, the unit serial and the firmware come from.
    with(download().first().source) {
      assertThat(product).isEqualTo("Dynon SkyView")
      assertThat(identity).isEqualTo("N1234X")
      assertThat(system_id).isEqualTo("SN0001")
      assertThat(software_version).isEqualTo("15.3.4.4867")
    }
    // A file renamed by hand yields nothing rather than a guess.
    with(single().single().source) {
      assertThat(product).isEqualTo("Dynon SkyView")
      assertThat(identity).isEmpty()
      assertThat(system_id).isEmpty()
    }
  }

  @Test
  fun columnNamesCarryTheirOwnUnitAndReachACanonicalId() = runTest {
    val parsed = single().single()
    fun series(name: String) = parsed.series.single { it.name == name }

    assertThat(series("Indicated Airspeed").unit).isEqualTo("knots")
    assertThat(series("Indicated Airspeed").canonical_id).isEqualTo(
      CanonicalSeries.IAS
    )
    assertThat(series("Oil Pressure").unit).isEqualTo("PSI")
    assertThat(series("Oil Pressure").canonical_id).isEqualTo("engine[1].oil_press")
    // Left and right, not one and two.
    assertThat(series("RPM L").canonical_id).isEqualTo("engine[1].rpm")
    assertThat(series("Fuel Level L").canonical_id).isEqualTo(
      CanonicalSeries.fuelQty(
        1
      )
    )
    assertThat(series("Ground Speed").canonical_id).isEqualTo(CanonicalSeries.GROUND_SPEED)
  }

  @Test
  fun percentPowerIsAPercentageTheHeaderNeverLabels() = runTest {
    // The column is named for its unit rather than carrying one in parentheses, which left the
    // sidebar showing a bare range. The values are already 0 to 100-odd, so this is a label and
    // nothing is scaled — unlike a G1000, whose percent columns really do hold a fraction.
    val power = download()[2].series.single { it.name == "Percent Power" }
    assertThat(power.unit).isEqualTo("%")
    assertThat(power.canonical_id).isEqualTo("engine[1].power_pct")
    assertThat(power.min).isEqualTo(0.0)
    assertThat(power.max).isEqualTo(33.0)
  }

  @Test
  fun positionCollapsesAndTheTimeColumnsStayOutOfTheCatalogue() = runTest {
    val parsed = single().single()
    val position =
      parsed.series.single { it.kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION }
    assertThat(position.canonical_id).isEqualTo(CanonicalSeries.POSITION)
    assertThat(position.sample_count).isEqualTo(400)
    assertThat(parsed.data.position).isNotNull()
    assertThat(parsed.series.map { it.name }).containsNoneOf(
      "Session Time",
      "GPS Date & Time",
      "System Time",
      "Longitude",
    )
  }

  @Test
  fun theGenericThermocoupleChannelsAreEmptyInBothSamplesAndSoAreDropped() =
    runTest {
      // The requirements assume a SkyView needs a channel-mapping prompt because its engine columns
      // are generic. Neither sample populates one: the installer labels the channels in the unit, and
      // the export writes those labels instead. Nothing here exercises a prompt, which is why none
      // was built. See design §6.2.
      val names = single().single().series.map { it.name }
      assertThat(names.filter { it.startsWith("Thermocouple") }).isEmpty()
      assertThat(names).contains("CHT L TEMPERATURE")
    }

  @Test
  fun groundSpeedDecidesAirborneHereTheSameWayItDoesOnAGarmin() = runTest {
    // The rule is written against a canonical id, so a third recorder costs it nothing.
    assertThat(DerivedFields.airborne(single().single())).isFalse()
  }

  @Test
  fun aFileThatIsNotASkyViewFails() = runTest {
    var threw = false
    try {
      parser.parse("a,b\n1,2\n".encodeToByteArray(), "x.csv")
    } catch (e: DataLogParseException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }
}
