package dev.fanfly.wingslog.feature.datalog.datamanager.avidyne

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.datamanager.Confidence
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParseException
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

/**
 * The Avidyne Entegra parser (design §6.2).
 *
 * As with the Garmin and Dynon tests, every expected value was derived from the log with an
 * independent script rather than from the parser, so a parser bug reads as a mismatch instead of
 * being pinned as the answer.
 */
class AvidyneParserTest {

  private val parser = AvidyneParser()

  private suspend fun log(name: String) =
    parser.parse(Fixtures.avidyneBytes(name), name).single()

  @Test
  fun theTitleLineIsTheWholeSignature() {
    fun sniff(text: String) = parser.sniff(text.encodeToByteArray())

    assertThat(sniff("Avidyne Engine Data Log\n1/1/09 10:00:00\n")).isEqualTo(Confidence.DEFINITE)
    assertThat(sniff("Avidyne Engine Data Log; DAU Software ID: 5.0\n"))
      .isEqualTo(Confidence.DEFINITE)
    assertThat(sniff("Session Time,GPS Fix Quality\n")).isEqualTo(Confidence.NONE)
    assertThat(sniff("#airframe_info,product=\"GDU 460\"\n")).isEqualTo(Confidence.NONE)
    assertThat(sniff("")).isEqualTo(Confidence.NONE)
  }

  @Test
  fun theStartComesFromTheHeaderBecauseNoRowCarriesADate() = runTest {
    val parsed = log(Fixtures.AVIDYNE_PLAIN)
    assertThat(parsed.format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_AVIDYNE)
    // Line 2 says 2/12/06 15:23:26 and the first kept row is 15:23:30.
    assertThat(parsed.start).isEqualTo(Instant.parse("2006-02-12T15:23:30Z"))
    assertThat(parsed.sampleCount).isEqualTo(299)
    assertThat(parsed.durationSeconds).isEqualTo(1830)
    // The file states no zone; its clock is the aeroplane's local time and is reported as written.
    assertThat(parsed.utcOffsetMinutes).isEqualTo(0)
  }

  @Test
  fun theUnitsOwnPowerOnFrameIsDroppedBecauseItPredatesTheLog() = runTest {
    // Before the log it just announced, the unit writes one frame of every sensor default at once:
    // outside air at -40, oil at -19, the engine stopped. Its clock is a few seconds EARLIER than
    // the start on line 2, which is what identifies it rather than it merely being first.
    val parsed = log(Fixtures.AVIDYNE_PLAIN)
    assertThat(parsed.series.single { it.name == "OAT" }.min).isEqualTo(2.0)
    assertThat(parsed.series.single { it.name == "RPM" }.min).isEqualTo(910.0)
    // Not a blanket "drop anything before the start": this file's oil temperature really does read
    // -19 for its first several rows, and those are kept.
    assertThat(parsed.series.single { it.name == "OILT" }.min).isEqualTo(-19.0)
  }

  @Test
  fun aLogThatRunsPastMidnightAdvancesTheDate() = runTest {
    // Starts at 23:35 and the clock reaches 00:00 two hundred rows later. With no date on any row,
    // the rollover is the only thing that can carry the log into the next day.
    val parsed = log(Fixtures.AVIDYNE_DATE_WRAP)
    assertThat(parsed.start).isEqualTo(Instant.parse("2005-09-11T23:35:12Z"))
    assertThat(parsed.sampleCount).isEqualTo(399)
    // 41 minutes, which only adds up if the clock crossing zero added a day rather than rewinding.
    assertThat(parsed.durationSeconds).isEqualTo(2466)
    assertThat(parsed.data.timeSeconds.last()).isEqualTo(2466)
  }

  @Test
  fun aUnitCorrectingItsClockIsNotAnotherDay() = runTest {
    // Four rows in, this log's clock steps back by 84 seconds. Read as a rollover it would have put
    // the rest of the flight on the following day; read as nothing it would have run the time axis
    // backwards. It keeps row order and moves on by a nominal second, as a Garmin's GPS step does.
    val parsed = log(Fixtures.AVIDYNE_TIME_JUMP)
    assertThat(parsed.start).isEqualTo(Instant.parse("2009-01-21T19:18:12Z"))
    assertThat(parsed.data.timeSeconds.take(8)).containsExactly(0, 6, 12, 13, 14, 15, 16, 17)
      .inOrder()
    assertThat(parsed.durationSeconds).isEqualTo(1716)
    assertThat(parsed.data.timeSeconds.toList()).isInOrder()
  }

  @Test
  fun theUnitsComeFromTheParserBecauseTheFileStatesNone() = runTest {
    val parsed = log(Fixtures.AVIDYNE_TURBO)
    fun series(name: String) = parsed.series.single { it.name == name }

    // Fahrenheit, settled by the numbers: a Celsius exhaust gas temperature runs around 800.
    assertThat(series("E1").unit).isEqualTo("°F")
    assertThat(series("E1").max).isEqualTo(1477.0)
    assertThat(series("C2").unit).isEqualTo("°F")
    assertThat(series("OAT").unit).isEqualTo("°C")
    assertThat(series("MAP").unit).isEqualTo("inHg")
    assertThat(series("FF").unit).isEqualTo("gph")
    assertThat(series("MBUS").unit).isEqualTo("volts")
    assertThat(series("PALT").unit).isEqualTo("ft")
  }

  @Test
  fun theTerseNamesReachTheSameCanonicalIdsTheOtherRecordersDo() = runTest {
    val parsed = log(Fixtures.AVIDYNE_TURBO)
    fun canonical(name: String) = parsed.series.single { it.name == name }.canonical_id

    assertThat(canonical("E4")).isEqualTo("engine[1].egt[4]")
    assertThat(canonical("C4")).isEqualTo("engine[1].cht[4]")
    assertThat(canonical("OILT")).isEqualTo("engine[1].oil_temp")
    assertThat(canonical("RPM")).isEqualTo("engine[1].rpm")
    assertThat(canonical("MAP")).isEqualTo("engine[1].map")
    assertThat(canonical("TIT")).isEqualTo("engine[1].tit[1]")
    assertThat(canonical("OAT")).isEqualTo(CanonicalSeries.OAT)
    assertThat(canonical("PALT")).isEqualTo(CanonicalSeries.ALT_PRESSURE)
  }

  @Test
  fun theQuotedDiscreteColumnsStayTextBecauseTheyAreBitPatterns() = runTest {
    // "0001000" is four switches with the fourth closed. Read as a number it is one thousand, and
    // the chart draws a switch closing as a spike off the top of the pane.
    val parsed = log(Fixtures.AVIDYNE_TURBO)
    val out = parsed.series.single { it.name == "DOUT" }
    assertThat(out.kind).isEqualTo(DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT)
    assertThat(parsed.data.text.getValue(out.column)[0]).isEqualTo("0001000")
    val input = parsed.series.single { it.name == "DIN" }
    assertThat(parsed.data.text.getValue(input.column)[0]).isEqualTo("0000011")
  }

  @Test
  fun positionCollapsesAndARowWithNoFixIsAGapRatherThanNullIsland() = runTest {
    val parsed = log(Fixtures.AVIDYNE_PLAIN)
    val position =
      parsed.series.single { it.kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION }
    assertThat(position.canonical_id).isEqualTo(CanonicalSeries.POSITION)
    // The unit writes 0.0000 for both degrees until it has a fix; nine rows here never got one.
    assertThat(position.sample_count).isEqualTo(290)
    assertThat(parsed.data.position!!.latitude[0].isNaN()).isTrue()
    assertThat(parsed.series.map { it.name }).containsNoneOf("TIME", "LON")
  }

  @Test
  fun theSoftwareIdIsReadWhereTheUnitStatesOne() = runTest {
    assertThat(log(Fixtures.AVIDYNE_TURBO).source.software_version).isEqualTo("5.0")
    assertThat(log(Fixtures.AVIDYNE_TIME_JUMP).source.software_version).isEqualTo("533")
    // The older units say nothing after the title.
    assertThat(log(Fixtures.AVIDYNE_PLAIN).source.software_version).isEmpty()
    assertThat(log(Fixtures.AVIDYNE_PLAIN).source.product).isEqualTo("Avidyne Entegra")
  }

  @Test
  fun everyAvidyneSampleParses() = runTest {
    Fixtures.avidyneDir()
      .listFiles { f -> f.extension == "log" }!!
      .forEach { file ->
        val parsed = parser.parse(file.readBytes(), file.name)
          .single()
        assertThat(parsed.format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_AVIDYNE)
        assertThat(parsed.sampleCount).isGreaterThan(200)
        assertThat(parsed.data.position).isNotNull()
        assertThat(parsed.data.timeSeconds.toList()).isInOrder()
      }
  }

  @Test
  fun aFileThatIsNotAvidyneFails() = runTest {
    var threw = false
    try {
      parser.parse("a,b\n1,2\n".encodeToByteArray(), "x.log")
    } catch (e: DataLogParseException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }
}
