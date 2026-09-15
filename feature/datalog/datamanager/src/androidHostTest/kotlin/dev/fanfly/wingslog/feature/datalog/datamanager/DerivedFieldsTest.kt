package dev.fanfly.wingslog.feature.datalog.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DerivedFieldsTest {

  private val parser = GarminParser()
  private val columns = listOf(
    Triple("GPS Ground Speed", "kt", "GndSpd"),
    Triple("Height Above Ground", "ft", "AGL"),
    Triple("Latitude", "deg", "Latitude"),
    Triple("Longitude", "deg", "Longitude"),
  )

  private suspend fun parse(rows: List<List<String>>) =
    parser.parse(
      Fixtures.synthetic(columns, rows),
      "log_20260902_144756_XX1.csv"
    ).single()

  @Test
  fun theFixtureIsAGroundRun() = runTest {
    val parsed =
      parser.parse(Fixtures.bytes(Fixtures.GROUND_RUN), Fixtures.GROUND_RUN).single()
    assertThat(DerivedFields.airborne(parsed)).isFalse()
    assertThat(DerivedFields.endPosition(parsed)).isEqualTo(39.0810252 to -114.1003005)
  }

  @Test
  fun groundSpeedAboveThirtyKnotsMeansAirborne() = runTest {
    val slow =
      parse(Fixtures.syntheticRows(5) { listOf("12.5", "", "+37.1", "-121.6") })
    val fast = parse(Fixtures.syntheticRows(5) { i ->
      listOf(
        if (i == 3) "31" else "5",
        "",
        "+37.1",
        "-121.6"
      )
    })
    assertThat(DerivedFields.airborne(slow)).isFalse()
    assertThat(DerivedFields.airborne(fast)).isTrue()
  }

  @Test
  fun heightAboveGroundAboveFiftyFeetMeansAirborneEvenWhenSlow() = runTest {
    val hover = parse(Fixtures.syntheticRows(5) { i ->
      listOf(
        "2",
        if (i == 4) "51" else "3",
        "",
        ""
      )
    })
    assertThat(DerivedFields.airborne(hover)).isTrue()
    assertThat(DerivedFields.endPosition(hover)).isNull()
  }

  @Test
  fun endPositionIsTheLastRowWithAFix() = runTest {
    val parsed = parse(
      Fixtures.syntheticRows(4) { i ->
        when (i) {
          2 -> listOf("0", "", "+37.5", "-121.5")
          else -> listOf("0", "", "", "")
        }
      },
    )
    assertThat(DerivedFields.endPosition(parsed)).isEqualTo(37.5 to -121.5)
  }

  @Test
  fun theFilenameIdentIsReadOnlyFromTheGarminPattern() {
    assertThat(DerivedFields.startLocationIdent("log_20260902_144756_XX1.csv")).isEqualTo(
      "XX1"
    )
    assertThat(DerivedFields.startLocationIdent("log_20260902_144756_KSQL.CSV")).isEqualTo(
      "KSQL"
    )
    // A G1000 writes a six-digit date where a G3X writes eight.
    assertThat(DerivedFields.startLocationIdent("log_240810_104802_KAPA.csv")).isEqualTo(
      "KAPA"
    )
    assertThat(DerivedFields.startLocationIdent("flight.csv")).isEmpty()
    assertThat(DerivedFields.startLocationIdent("log_2026_KSQL.csv")).isEmpty()
    assertThat(DerivedFields.startLocationIdent("log_2408_104802_KAPA.csv")).isEmpty()
  }

  @Test
  fun identityMismatchIgnoresCaseAndPunctuationAndBlanks() {
    assertThat(DerivedFields.identityMismatch("N1234X", "n-1234x")).isFalse()
    assertThat(DerivedFields.identityMismatch("N1234X", "N5678Y")).isTrue()
    assertThat(DerivedFields.identityMismatch("", "N5678Y")).isFalse()
    assertThat(DerivedFields.identityMismatch("N1234X", null)).isFalse()
    assertThat(DerivedFields.identityMismatch("N1234X", "  ")).isFalse()
  }
}
