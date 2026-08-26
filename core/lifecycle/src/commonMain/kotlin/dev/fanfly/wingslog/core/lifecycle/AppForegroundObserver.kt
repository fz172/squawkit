package dev.fanfly.wingslog.core.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * App-level session boundaries: cold start, and returning to the foreground after long enough away.
 *
 * This exists because the codebase had no such concept. `AnalyticsManager` exposes only
 * `logScreenView` / `logEvent` / `setAnalyticsCollectionEnabled`; Firebase keeps its own session
 * internal and surfaces neither an id nor a boundary. The only lifecycle hook was a **screen**-scoped
 * `LifecycleResumeEffect` (added for the #373 due-status recompute), which fires on every tab switch
 * and is far too eager to bound anything app-wide.
 *
 * **This class is deliberately not Compose-aware and has no `expect`/`actual`.** It is a plain state
 * machine driven from outside by [onEnterForeground] / [onEnterBackground]. The Compose-side driver
 * lives at the shell root, where one `LifecycleResumeEffect` already works on all three hosts (UIKit
 * foreground on iOS, `document.visibilitychange` on web). Splitting it this way keeps the logic
 * unit-testable with a fake clock and no Compose test harness, and avoids three per-host actuals over
 * `ProcessLifecycleOwner`, `NSNotificationCenter` and `visibilitychange` for one signal.
 *
 * Scoped as minimum-viable for the ad session cap — one counter, one threshold — rather than as a
 * general analytics session feature. If the analytics taxonomy later wants a session id, it should
 * grow from here rather than introduce a second observer.
 *
 * Not thread-safe, and does not need to be: it is driven from Compose composition and read from the
 * UI, both on the main thread.
 */
class AppForegroundObserver(
  private val clock: Clock = Clock.System,
  private val threshold: Duration = DEFAULT_THRESHOLD,
) {

  private val _sessionId = MutableStateFlow(0L)

  /**
   * Increments once per session. `0` means the app has not yet come to the foreground; the cold
   * start takes it to `1`.
   *
   * A monotonically increasing id rather than a `Flow<Unit>` of events, because the consumer
   * ([dev.fanfly.wingslog.feature.ads.datamanager.AdSessionCounter]) needs to know *whether the
   * session changed* at an arbitrary later moment, not to be notified at the instant it did. Reading
   * a value cannot miss an edge; a subscription started after the emission can.
   */
  val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

  private var backgroundedAt: Instant? = null

  /**
   * Call when the app becomes visible. Starts a new session on cold start, or when the app has been
   * backgrounded for at least [threshold]; otherwise this is the same session resuming and nothing
   * changes.
   */
  fun onEnterForeground() {
    val since = backgroundedAt
    val startsNewSession = _sessionId.value == 0L ||
      (since != null && clock.now() - since >= threshold)
    backgroundedAt = null
    if (startsNewSession) _sessionId.value = _sessionId.value + 1
  }

  /** Call when the app stops being visible. Starts the clock that decides the next boundary. */
  fun onEnterBackground() {
    backgroundedAt = clock.now()
  }

  companion object {
    /**
     * Matches the 30 minutes the PRD specifies for the ad session cap. Long enough that a glance at
     * a notification and back does not reset a pilot's ad budget, short enough that a genuinely new
     * working session is treated as one.
     */
    val DEFAULT_THRESHOLD: Duration = 30.minutes
  }
}
