package dev.fanfly.wingslog.feature.datalog.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import org.junit.Test

/** The sniff table of design §6.2 and §14. */
class HeaderSnifferTest {

  private val garmin = GarminParser()
  private val sniffer = HeaderSniffer(listOf(garmin))

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
  fun g1000HeaderIsPossibleUntilItsParserLands() {
    assertThat(sniff("#airframe_info, log_version=\"1.00\", airframe_name=\"Cessna 172S\"\n#yyy-mm-dd\n"))
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
