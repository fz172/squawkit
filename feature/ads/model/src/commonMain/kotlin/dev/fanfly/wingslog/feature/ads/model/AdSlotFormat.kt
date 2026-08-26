package dev.fanfly.wingslog.feature.ads.model

/**
 * How much horizontal room a slot has, in the only three buckets ad layout cares about.
 *
 * This deliberately does **not** reuse `LayoutTier` from `core/ui/adaptive`. That type lives in a
 * Compose module, and `feature/ads/model` is dependency-free on purpose so the three list surfaces
 * can depend on the placement core without pulling Compose into it (design §2, N4). The mapping is
 * one line at the viewing layer, where `LayoutTier` is already in scope:
 *
 * ```
 * COMPACT -> COMPACT ; MEDIUM -> MEDIUM ; EXPANDED, LARGE -> WIDE
 * ```
 *
 * `EXPANDED` and `LARGE` collapse together because §7.1 treats them identically — both are wide
 * enough for two thin units side by side, and nothing about ad layout distinguishes them.
 */
enum class AdLayoutTier {
  /** Phone. One unit at the card's own width; a 320 dp banner is already about that wide. */
  COMPACT,

  /** Tablet portrait. Wide enough to look stretched, not wide enough for two readable units. */
  MEDIUM,

  /** Tablet landscape and desktop. Two thin units side by side read as content; one reads as a takeover. */
  WIDE,
}

/**
 * How many ad units a slot renders, and therefore how many it costs against the session cap.
 *
 * Width buys **count**, never size (§7.1). A wide band never gets a taller or stretched creative —
 * that is the difference between an ad in a maintenance list and a billboard in one.
 */
enum class AdSlotFormat(val unitCount: Int) {
  /** One unit. Full card width on COMPACT; centred with neutral padding on MEDIUM and above. */
  SINGLE(1),

  /** Two thin units side by side, each centred in half the band minus the grid gutter. */
  TWO_UP(2);

  companion object {

    /**
     * How many units a slot at [tier] would *like*, before the session budget has its say.
     *
     * Deliberately no headroom parameter. An earlier version resolved the format from remaining
     * headroom and returned null at zero — which meant a slot bailed out before asking the counter,
     * and so a slot that already held a grant showed nothing once the cap was reached. Only
     * `AdsManager.reserve` may decide what a slot renders, because only it knows what that slot was
     * already given.
     */
    fun desiredUnits(tier: AdLayoutTier): Int =
      if (tier == AdLayoutTier.WIDE) TWO_UP.unitCount else SINGLE.unitCount

    /**
     * The format to render for a grant of [grantedUnits], as returned by `AdsManager.reserve`.
     *
     * A grant of one on a wide tier renders centred, exactly as the MEDIUM case — that is §7.1's
     * "near the cap" rule falling out of the grant rather than being special-cased.
     */
    fun forGrant(grantedUnits: Int): AdSlotFormat? = when {
      grantedUnits <= 0 -> null
      grantedUnits >= TWO_UP.unitCount -> TWO_UP
      else -> SINGLE
    }
  }
}
