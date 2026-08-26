package dev.fanfly.wingslog.feature.ads.datamanager.impl

import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.feature.ads.model.AdSlotKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The hard ceiling on how many ad units a pilot sees in one app session — **5**, counted globally
 * across squawks, tasks and logs rather than per list. Someone who scrolls a 400-entry logbook,
 * switches to squawks and comes back sees five ads, not sixty.
 *
 * The cadence (one slot per 10 records) decides *where* ads may appear; this decides *how many*
 * actually do. At those two numbers the cap binds early and often — a single 60-item list exhausts
 * it — which is intentional: the cadence spaces ads out *within* a list, and the cap is what holds a
 * whole working session to a handful of impressions no matter how much scrolling it contains.
 *
 * **Must be a Koin `single`.** A second instance would be a second budget, silently multiplying a
 * pilot's exposure by however many instances exist.
 *
 * **The cap also does memory work, not just UX work.** Two of the three surfaces
 * (`AdaptiveCardList`, used by squawks and tasks) are *not* lazy — they are plain rows inside an
 * outer scroll, so every composed ad view stays alive with nothing recycling it. This cap is what
 * bounds live ad views to five. Raising it later is therefore a memory decision as well as a product
 * one; that trade-off should not be rediscovered at the time.
 *
 * Not thread-safe, and does not need to be: reservations happen during Compose composition on the
 * main thread.
 */
internal class AdSessionCounter(
  private val foreground: AppForegroundObserver,
) {

  private var observedSessionId = 0L
  private val _displayed = MutableStateFlow(0)

  /**
   * What each slot was granted this session.
   *
   * The grant belongs to the **slot**, not to the composable that happens to be showing it. A slot
   * scrolled out of the lazy logs list, or on a tab the pilot leaves, is disposed and comes back as
   * a fresh composable — so without this, revisiting a slot either spends the budget a second time
   * or, once the cap is reached, is granted nothing and the ad the pilot already saw disappears.
   */
  private val grants = mutableMapOf<AdSlotKey, Int>()

  /** Slots whose impression has already been counted, so revisiting one is not a second impression. */
  private val impressionsLogged = mutableSetOf<AdSlotKey>()

  /** Units displayed so far this session, `0..`[CAP]. Resets at each session boundary. */
  val displayed: StateFlow<Int> = _displayed.asStateFlow()

  private val _capReached = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /**
   * Emits **once per session**, on the reservation that consumes the final unit. Backs the
   * `ad_session_cap_reached` event, which tells us how often the cap actually binds — if it fires
   * for most sessions, the cap rather than the cadence is the constraint shaping exposure, and that
   * is a product conversation rather than a config tweak.
   */
  val capReached: SharedFlow<Unit> = _capReached.asSharedFlow()

  /** Units still available this session. Never negative. */
  val headroom: Int
    get() {
      syncSession()
      return CAP - _displayed.value
    }

  /**
   * Claims up to [units] of the session budget for a slot about to display, returning how many were
   * actually granted — `0`, `1`, or `2`.
   *
   * The **partial grant** is the point. A two-up slot asking for 2 with only 1 unit left gets 1,
   * and format resolution then renders that single unit centred exactly as the MEDIUM case does. So
   * "near the cap" needs no special case at any call site, a band is never shown half-empty, and
   * overshooting the cap is not expressible rather than merely discouraged.
   *
   * A grant of `0` means the slot renders at **zero height**: no request, no label, no gap. Ads
   * already displayed stay as they are — nothing disappears from under the user.
   */
  fun reserve(key: AdSlotKey, units: Int): Int {
    require(units > 0) { "Must reserve at least one unit, was $units" }
    syncSession()

    // Idempotent per slot: a slot that already holds a grant keeps it, spending nothing further.
    // This is what N8 means by caching the fill per slot key — scroll churn must cost no budget,
    // and a slot the pilot scrolls back to must still be there even once the cap has been reached.
    grants[key]?.let { return it }

    val granted = minOf(units, CAP - _displayed.value).coerceAtLeast(0)
    if (granted == 0) return 0

    grants[key] = granted
    _displayed.value += granted
    if (_displayed.value >= CAP) _capReached.tryEmit(Unit)
    return granted
  }

  /**
   * True the first time it is called for [key] in a session, false afterwards.
   *
   * Guards the impression event specifically: a slot revisited by scrolling is the *same* impression,
   * so counting it again would inflate the number the whole §12 revenue picture rests on.
   */
  fun markImpressionLogged(key: AdSlotKey): Boolean {
    syncSession()
    return impressionsLogged.add(key)
  }


  /**
   * Returns [units] previously claimed by [reserve] that never became a visible ad — a request that
   * failed, or a slot torn down before it filled.
   *
   * The budget is claimed at *request* time so two slots composing together cannot both spend the
   * last unit, but the PRD counts only **filled** units: "unfilled slots, collapsed slots, and
   * failed requests count as zero." Without a refund a run of no-fills would silently consume a
   * pilot's whole session allowance and then show them nothing.
   *
   * This is not a way to get extra ads: it only ever gives back what a caller already took, and the
   * floor at zero means over-releasing cannot manufacture headroom.
   */
  fun release(key: AdSlotKey, units: Int) {
    require(units > 0) { "Must release at least one unit, was $units" }
    val held = grants[key] ?: return
    val given = minOf(units, held)
    if (given <= 0) return

    // Forget the grant entirely once nothing is left, so a later composition may try again — a
    // no-fill is usually the network, not the slot, and the pilot should not be permanently short
    // an ad slot because one request happened to fail.
    val remaining = held - given
    if (remaining == 0) grants.remove(key) else grants[key] = remaining
    _displayed.value = (_displayed.value - given).coerceAtLeast(0)
  }

  /**
   * Resets the budget when the app session has rolled over.
   *
   * Polled here rather than collected from a flow, deliberately: the counter has no coroutine scope
   * of its own, and a subscription that started late — or was cancelled while the screen was gone —
   * could miss a boundary and let a stale count run into the next session. Reading a monotonic id at
   * the moment of use cannot miss an edge.
   */
  private fun syncSession() {
    val current = foreground.sessionId.value
    if (current != observedSessionId) {
      observedSessionId = current
      _displayed.value = 0
      grants.clear()
      impressionsLogged.clear()
    }
  }

  companion object {
    /** PRD D1. Lowered from 10 in review; see the PRD before changing it. */
    const val CAP: Int = 5
  }
}
