package dev.fanfly.wingslog.feature.ads.model

/**
 * The fixed ad unit sizes v1 requests (PRD D7, design §7.2). **Fixed, not inline adaptive**, so the
 * no-billboard guarantee (G10) and the height cap (N9) hold by construction rather than by clamping
 * a network-chosen height at runtime: a 320 dp-wide creative cannot stretch into a leaderboard.
 *
 * [heightDp] is the creative's own height, which is what G10 compares against the record card beside
 * it. Both values clear the 120 dp cap numerically; only the card-relative comparison can rule one
 * out — which is why [LARGE_BANNER] is not usable until that measurement is done.
 */
enum class AdUnitSize(val widthDp: Int, val heightDp: Int) {
  /** 320×50. The default everywhere, and the only size v1 ships until [LARGE_BANNER] is cleared. */
  BANNER(320, 50),

  /**
   * 320×100. **Do not use without measuring first.** Record cards have no fixed height — a
   * `SquawkCard` is a `Card` + `Column` sized by content, so a short squawk lands around 90–110 dp.
   * At exactly 100 dp this sits on that boundary and would breach G10 next to a short card. Gated on
   * the card-height measurement; if cards measure under ~110 dp it is out on phones regardless of
   * its revenue advantage, because G10 is a guardrail rather than a preference.
   */
  LARGE_BANNER(320, 100),
  ;

  companion object {
    /** The hard ceiling from N9, independent of the sizes above. */
    const val MAX_HEIGHT_DP = 120
  }
}
