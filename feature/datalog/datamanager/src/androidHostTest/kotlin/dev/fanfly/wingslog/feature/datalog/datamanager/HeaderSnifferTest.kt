package dev.fanfly.wingslog.feature.datalog.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.datamanager.avidyne.AvidyneParser
import dev.fanfly.wingslog.feature.datalog.datamanager.dynon.DynonParser
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import org.junit.Test

/** The sniff table of design §6.2 and §14. */
class HeaderSnifferTest {

  private val garmin = GarminParser()
  private val dynon = DynonParser()
  private val avidyne = AvidyneParser()
  private val sniffer = HeaderSniffer(listOf(garmin, dynon, avidyne))

  private fun sniff(text: String) = garmin.sniff(text.encodeToByteArray())

  @Test
  fun g3xHeaderIsDefinite() {
    assertThat(sniff("#airframe_info,log_version=\"1.00\",product=\"GDU 460\",aircraft_ident=\"N1\"\nDate\n"))
      .isEqualTo(Confidence.DEFINITE)
    assertThat(
      garmin.sniff(
        Fixtures.bytes(Fixtures.GROUND_RUN)
          .copyOf(HeaderSniffer.SNIFF_BYTES)
      )
    )
      .isEqualTo(Confidence.DEFINITE)
  }

  @Test
  fun g1000HeaderIsDefinite() {
    // `airframe_name` is only ever written by a G1000, so the key alone settles it.
    assertThat(sniff("#airframe_info, log_version=\"1.00\", airframe_name=\"Cessna 172S\"\n#yyy-mm-dd\n"))
      .isEqualTo(Confidence.DEFINITE)
    listOf(Fixtures.G1000_PISTON, Fixtures.G1000_TURBINE, Fixtures.G1000_CRUISE)
      .forEach { name ->
        assertThat(
          garmin.sniff(
            Fixtures.g1000Bytes(name)
              .copyOf(HeaderSniffer.SNIFF_BYTES)
          )
        ).isEqualTo(Confidence.DEFINITE)
      }
  }

  @Test
  fun aGarminHeaderNamingNeitherKeyIsStillWorthATry() {
    // Every Garmin log starts this way; a variant we have not seen goes to the Garmin parser rather
    // than to nobody, and fails in parse() if the body contradicts the guess.
    assertThat(sniff("#airframe_info, log_version=\"9.99\"\nDate\n"))
      .isEqualTo(Confidence.POSSIBLE)
  }

  @Test
  fun wrongFilesAreNone() {
    assertThat(sniff("")).isEqualTo(Confidence.NONE)
    assertThat(sniff("Date,Time,Speed\n2026-01-01,10:00:00,5\n")).isEqualTo(
      Confidence.NONE
    )
    assertThat(sniff("<html><body>not a log</body></html>")).isEqualTo(
      Confidence.NONE
    )
    assertThat(
      garmin.sniff(
        byteArrayOf(
          0x50,
          0x4B,
          0x03,
          0x04,
          -1,
          -2,
          0,
          0x7f
        )
      )
    ).isEqualTo(Confidence.NONE)
  }

  @Test
  fun aByteOrderMarkDoesNotHideTheHeader() {
    assertThat(sniff("\uFEFF#airframe_info,product=\"GDU 460\"\n")).isEqualTo(
      Confidence.DEFINITE
    )
  }

  @Test
  fun aSkyViewGoesToTheDynonParserAndNeverToTheGarminOne() {
    val skyView = Fixtures.dynonBytes(Fixtures.DYNON_SINGLE)
      .copyOf(HeaderSniffer.SNIFF_BYTES)
    assertThat(dynon.sniff(skyView)).isEqualTo(Confidence.DEFINITE)
    assertThat(garmin.sniff(skyView)).isEqualTo(Confidence.NONE)
    assertThat(sniffer.sniff(skyView)).isSameInstanceAs(dynon)

    // And the reverse, so two parsers in one list cannot start claiming each other's files.
    val g3x = Fixtures.bytes(Fixtures.GROUND_RUN)
      .copyOf(HeaderSniffer.SNIFF_BYTES)
    assertThat(dynon.sniff(g3x)).isEqualTo(Confidence.NONE)
    assertThat(sniffer.sniff(g3x)).isSameInstanceAs(garmin)
  }

  @Test
  fun everyParserRecognisesOnlyItsOwnFormat() {
    // Three parsers in one list, so the table matters more than any single verdict: each file must
    // reach exactly one of them, and the other two must say NONE rather than POSSIBLE.
    val files = mapOf(
      garmin to Fixtures.bytes(Fixtures.GROUND_RUN),
      dynon to Fixtures.dynonBytes(Fixtures.DYNON_SINGLE),
      avidyne to Fixtures.avidyneBytes(Fixtures.AVIDYNE_PLAIN),
    )

    files.forEach { (owner, bytes) ->
      val header = bytes.copyOf(HeaderSniffer.SNIFF_BYTES)
      assertThat(owner.sniff(header)).isEqualTo(Confidence.DEFINITE)
      assertThat(sniffer.sniff(header)).isSameInstanceAs(owner)
      files.keys.filterNot { it == owner }
        .forEach { other -> assertThat(other.sniff(header)).isEqualTo(Confidence.NONE) }
    }
  }

  @Test
  fun theSnifferPicksTheParserOrNothing() {
    assertThat(sniffer.sniff("#airframe_info,product=\"GDU 460\"\n".encodeToByteArray())).isSameInstanceAs(
      garmin
    )
    assertThat(sniffer.sniff("#airframe_info,airframe_name=\"x\"\n".encodeToByteArray())).isSameInstanceAs(
      garmin
    )
    assertThat(sniffer.sniff("nope".encodeToByteArray())).isNull()
    assertThat(sniffer.sniff(ByteArray(0))).isNull()
  }
}
