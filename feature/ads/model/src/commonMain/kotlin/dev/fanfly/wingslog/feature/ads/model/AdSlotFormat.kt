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
     * Resolves the format for a slot from its layout tier and the session cap's remaining
     * [headroom] (see `AdSessionCounter`, P3).
     *
     * Returns `null` when no unit may render at all — headroom exhausted — which the caller renders
     * as **zero height**: no request, no label, no gap (G5).
     *
     * Folding headroom in here is what keeps §7.1's "near the cap" rule from becoming a special case
     * at every call site: a WIDE slot that would render two-up but has only one unit of headroom
     * left degrades to [SINGLE] and is centred, exactly as the MEDIUM case. A band is never shown
     * half-empty, and the cap is never overshot.
     */
    fun of(tier: AdLayoutTier, headroom: Int): AdSlotFormat? = when {
      headroom <= 0 -> null
      tier == AdLayoutTier.WIDE && headroom >= TWO_UP.unitCount -> TWO_UP
      else -> SINGLE
    }
  }
}
