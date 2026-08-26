package dev.fanfly.wingslog.feature.ads.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdSlotFormatTest {

  @Test
  fun `wide tiers ask for two units, narrower ones ask for one`() {
    assertThat(AdSlotFormat.desiredUnits(AdLayoutTier.COMPACT)).isEqualTo(1)
    assertThat(AdSlotFormat.desiredUnits(AdLayoutTier.MEDIUM)).isEqualTo(1)
    assertThat(AdSlotFormat.desiredUnits(AdLayoutTier.WIDE)).isEqualTo(2)
  }

  @Test
  fun `the format follows the grant, not the tier`() {
    // §7.1's "near the cap" rule: a wide slot granted one unit renders it centred, exactly as the
    // MEDIUM case. That falls out of reading the grant instead of re-deciding from the tier.
    assertThat(AdSlotFormat.forGrant(1)).isEqualTo(AdSlotFormat.SINGLE)
    assertThat(AdSlotFormat.forGrant(2)).isEqualTo(AdSlotFormat.TWO_UP)
  }

  @Test
  fun `no grant means no format, which the caller renders as nothing at all`() {
    assertThat(AdSlotFormat.forGrant(0)).isNull()
    assertThat(AdSlotFormat.forGrant(-1)).isNull()
  }

  @Test
  fun `asking never depends on remaining budget`() {
    // Regression guard for the disappearing-ad bug. The resolver used to take remaining headroom and
    // return null at zero, so a slot short-circuited before asking the counter — and a slot that
    // already held a grant rendered nothing once the cap was reached. What a slot *asks* for is a
    // property of the layout alone; what it *gets* is the counter's business.
    AdLayoutTier.entries.forEach { tier ->
      assertThat(AdSlotFormat.desiredUnits(tier)).isGreaterThan(0)
    }
  }

  @Test
  fun `unit count is what the slot costs against the session cap`() {
    assertThat(AdSlotFormat.SINGLE.unitCount).isEqualTo(1)
    assertThat(AdSlotFormat.TWO_UP.unitCount).isEqualTo(2)
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
