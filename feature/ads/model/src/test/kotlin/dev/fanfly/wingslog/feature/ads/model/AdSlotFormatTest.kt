package dev.fanfly.wingslog.feature.ads.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdSlotFormatTest {

  @Test
  fun `wide tiers get two units, narrower ones get a single`() {
    assertThat(AdSlotFormat.of(AdLayoutTier.COMPACT, headroom = 5)).isEqualTo(AdSlotFormat.SINGLE)
    assertThat(AdSlotFormat.of(AdLayoutTier.MEDIUM, headroom = 5)).isEqualTo(AdSlotFormat.SINGLE)
    assertThat(AdSlotFormat.of(AdLayoutTier.WIDE, headroom = 5)).isEqualTo(AdSlotFormat.TWO_UP)
  }

  @Test
  fun `a two-up slot degrades to a single centred unit at one unit of headroom`() {
    // §7.1 "near the cap": rather than overshoot the cap or show a half-empty band, the slot
    // renders one centred unit - the MEDIUM presentation. Folding headroom into the resolver is
    // what keeps this from being a special case at every call site.
    assertThat(AdSlotFormat.of(AdLayoutTier.WIDE, headroom = 1)).isEqualTo(AdSlotFormat.SINGLE)
  }

  @Test
  fun `no headroom means no format, which the caller renders as zero height`() {
    AdLayoutTier.entries.forEach { tier ->
      assertThat(AdSlotFormat.of(tier, headroom = 0)).isNull()
      assertThat(AdSlotFormat.of(tier, headroom = -1)).isNull()
    }
  }

  @Test
  fun `unit count is what the slot costs against the session cap`() {
    assertThat(AdSlotFormat.SINGLE.unitCount).isEqualTo(1)
    assertThat(AdSlotFormat.TWO_UP.unitCount).isEqualTo(2)
    // A two-up slot never resolves without the headroom to pay for both units.
    assertThat(AdSlotFormat.of(AdLayoutTier.WIDE, headroom = AdSlotFormat.TWO_UP.unitCount))
      .isEqualTo(AdSlotFormat.TWO_UP)
  }

  @Test
  fun `both shipped unit sizes clear the height cap`() {
    // N9 / G10: the numeric cap is satisfied by construction. Only the card-relative comparison
    // (Q8's measurement) can rule LARGE_BANNER out.
    AdUnitSize.entries.forEach { size ->
      assertThat(size.heightDp).isAtMost(AdUnitSize.MAX_HEIGHT_DP)
      assertThat(size.widthDp).isEqualTo(320)
    }
  }
}
