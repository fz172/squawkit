package dev.fanfly.wingslog.feature.ads.datamanager

import kotlinx.coroutines.flow.Flow

/**
 * The single thing the ad UI talks to. Owns both halves of "may this slot show an ad": the
 * entitlement gate (free tier, on a build that supports ads) and the session budget.
 *
 * Deliberately the *only* public surface of this module. `AdSessionCounter` is `internal`, so a
 * screen cannot reach past this and do its own budgeting — which would be an easy mistake to make
 * and a hard one to notice, since a second opinion about the cap looks exactly like a working
 * feature until someone counts the ads.
 */
interface AdsManager {

  /**
   * Whether ad slots should be interleaved at all. Reactive: the moment a purchase resolves this
   * goes `false` and in-flight slots collapse without a restart; on expiry it returns.
   *
   * When `false`, callers render their items directly rather than wrapping them, so the ad-free
   * path allocates nothing.
   */
  fun showsAds(): Flow<Boolean>

  /** Units still available this session, `0..5`. Feeds `AdSlotFormat.of()`. */
  fun headroom(): Int

  /**
   * Claims up to [units] of this session's budget for a slot about to display, returning how many
   * were granted — `0`, `1`, or `2`. A partial grant is normal and expected: a two-up slot asking
   * for 2 with one unit left gets 1 and renders that unit centred.
   *
   * `0` means render at zero height — no request, no label, no gap.
   */
  fun reserve(units: Int): Int

  /**
   * Gives back units claimed by [reserve] that never became a visible ad — a failed request, or a
   * slot torn down before it filled. Only filled units count against the session cap.
   */
  fun release(units: Int)

  /**
   * Emits once per session, when the final unit is displayed. Backs `ad_session_cap_reached`, which
   * tells us how often the cap actually binds.
   */
  val capReached: Flow<Unit>

}
