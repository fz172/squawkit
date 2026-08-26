package dev.fanfly.wingslog.feature.notifications.engine

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** One collaboration stream: what a single actor is doing to one record type on one aircraft. */
data class ActivityKey(
  val aircraftId: String,
  val recordType: String,
  val actorUid: String,
)

/**
 * What to post for a foreign write, or `null` when the write was counted but should not interrupt
 * anyone (design §7.4 step 5).
 *
 * @param changeCount writes in the current working session, which is what the body reports.
 * @param sessionStart the session's `firstWriteAt`, embedded in the notification tag so a finished
 *   session's tray entry is left alone rather than overwritten by the next one.
 */
data class ActivityPost(
  val key: ActivityKey,
  val changeCount: Int,
  val sessionStart: Instant,
)

/**
 * Web's local stand-in for the server-side `notification_activity` counter (design §8.4), applying
 * the same rules as §7.4 with none of the infrastructure.
 *
 * In-memory and per-tab, which is the whole point: a tab only ever sees writes the sync engine
 * delivered to it, so there is no contention, no hot document, and no `notification_rate` ceiling to
 * enforce. **No timers** — both windows are evaluated lazily on the next write, so this class has no
 * lifecycle to get wrong.
 *
 * Two rules, both from §7.4:
 *
 * - **[ACTIVITY_WINDOW] (30 min) ends a working session.** The next write past it resets
 *   `changeCount` *and* rolls [ActivityPost.sessionStart], which rolls the tag — so Tuesday
 *   afternoon and Wednesday morning are two tray entries reading "5 changes" and "3 changes", never
 *   one reading "8" and never one silently replacing the other.
 * - **[MIN_REPOST_INTERVAL] (30s) is the per-key storm guard.** A bulk import of 200 records posts
 *   at most twice a minute per key instead of 200 times. The cost is a count that lags by up to 30
 *   seconds; the next write corrects it, and the last write of a burst is the one that matters.
 *
 * Not thread-safe, and does not need to be: JS is single-threaded, and this is bound only on web.
 */
class ActivityCounter(
  private val clock: Clock = Clock.System,
  private val activityWindow: Duration = ACTIVITY_WINDOW,
  private val minRepostInterval: Duration = MIN_REPOST_INTERVAL,
) {

  private data class State(
    val changeCount: Int,
    val firstWriteAt: Instant,
    val lastWriteAt: Instant,
    val lastSentAt: Instant?,
  )

  private val states = mutableMapOf<ActivityKey, State>()

  /**
   * Records one foreign write and decides whether it should post.
   *
   * Counting always happens; only the posting is throttled. A suppressed write still increments the
   * session, so the next post that does get through reports the true total.
   */
  fun record(key: ActivityKey): ActivityPost? {
    val now = clock.now()
    val previous = states[key]

    val newSession = previous == null || now - previous.lastWriteAt > activityWindow
    val state = if (newSession) {
      State(changeCount = 1, firstWriteAt = now, lastWriteAt = now, lastSentAt = null)
    } else {
      previous.copy(changeCount = previous.changeCount + 1, lastWriteAt = now)
    }
    states[key] = state

    // Throttle against the last *send*, not the last write, and never against a previous session's
    // send — a new session has its own tray entry to fill, so it posts immediately.
    val lastSentAt = state.lastSentAt
    if (lastSentAt != null && now - lastSentAt < minRepostInterval) return null

    states[key] = state.copy(lastSentAt = now)
    return ActivityPost(
      key = key,
      changeCount = state.changeCount,
      sessionStart = state.firstWriteAt,
    )
  }

  /** Drops all state. Called on sign-out so one account's sessions cannot leak into the next. */
  fun clear() {
    states.clear()
  }

  companion object {
    /** §7.4: a gap this long ends the working session, resetting the count and rolling the tag. */
    val ACTIVITY_WINDOW: Duration = 30.minutes

    /** §7.4: the per-key storm guard. */
    val MIN_REPOST_INTERVAL: Duration = 30.seconds
  }
}
