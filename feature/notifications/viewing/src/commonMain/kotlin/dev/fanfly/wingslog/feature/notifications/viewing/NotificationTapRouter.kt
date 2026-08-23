package dev.fanfly.wingslog.feature.notifications.viewing

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SCHEME = "wingslog"
private const val HOST = "notification-tap"

/**
 * Platform-agnostic channel for delivering a tapped notification's target into the running app
 * (design §5.3). Same shape as `EmailLinkDeepLinks.pendingLink` — a `StateFlow` that retains its
 * last value — so cold start (buffered until a consumer subscribes) and warm tap (delivered
 * immediately) both work without a second mechanism.
 *
 * There are two consumers, split by whether the target has a real nav-graph destination:
 * - `Squawk`/`Task`/`Log` all have one already (`Screen.EditSquawk` and friends) — the host's
 *   `AppEntry` collects [pending] and calls `navController.navigate`.
 * - `Aircraft` does not, and per `AdaptiveShellViewModel`'s own doc comment, deliberately never
 *   will: "the selected aircraft is app-level state chosen from the switcher rather than a
 *   navigation argument carried per destination." `AdaptiveShellViewModel` collects [pending]
 *   itself and calls `selectAircraft`/`selectSection` directly — no nav route, no shim destination.
 *
 * [encode]/[decode] are symmetric and live together in the one file that owns the wire format, a
 * plain `"$SCHEME://$HOST/…"` string — deliberately not `android.net.Uri`, which doesn't exist
 * outside androidMain. [AndroidLocalNotifier] calls [encode] when building a notification's tap
 * `PendingIntent`; `MainActivity.handleDeepLink` calls [deliver] (which calls [decode]) as one more
 * link in the existing `AircraftShareDeepLinks` / `EmailLinkDeepLinks` chain.
 */
object NotificationTapRouter {
  private val _pending = MutableStateFlow<NotificationTapTarget?>(null)
  val pending: StateFlow<NotificationTapTarget?> = _pending.asStateFlow()

  /**
   * Called by a host's launch-intent handler with whatever URI the OS handed it. Returns `false`
   * (delivering nothing) for a URI this router doesn't own, so callers can fall through to their
   * next deep-link handler — same contract as `AircraftShareDeepLinks.deliver`.
   */
  fun deliver(uri: String): Boolean {
    val target = decode(uri) ?: return false
    // Debug, not info: record ids. Paired with consume()'s line this is the whole delivery contract
    // in logcat — enough to tell "the tap never arrived" from "something consumed it too early",
    // which is exactly the distinction that is otherwise invisible from the outside.
    log.d { "deliver: target=$target (was pending=${_pending.value})" }
    _pending.value = target
    return true
  }

  /** Called once a tap target has been acted on, so it isn't re-delivered on the next recomposition or relaunch. */
  fun consume() {
    log.d { "consume: clearing pending=${_pending.value}" }
    _pending.value = null
  }

  private val log = Logger.withTag("NotificationTapRouter")

  fun encode(target: NotificationTapTarget): String = when (target) {
    is NotificationTapTarget.Squawk -> "$SCHEME://$HOST/squawk/${target.aircraftId}/${target.squawkId}"
    is NotificationTapTarget.Task -> "$SCHEME://$HOST/task/${target.aircraftId}/${target.taskId}"
    is NotificationTapTarget.Log -> "$SCHEME://$HOST/log/${target.aircraftId}/${target.logId}"
    is NotificationTapTarget.Aircraft ->
      "$SCHEME://$HOST/aircraft/${target.aircraftId}" + (target.tab?.let { "?tab=$it" } ?: "")
  }

  private fun decode(uri: String): NotificationTapTarget? {
    val prefix = "$SCHEME://$HOST/"
    if (!uri.startsWith(prefix)) return null
    val withoutPrefix = uri.removePrefix(prefix)
    val queryIndex = withoutPrefix.indexOf('?')
    val path = if (queryIndex >= 0) withoutPrefix.substring(0, queryIndex) else withoutPrefix
    val query = if (queryIndex >= 0) withoutPrefix.substring(queryIndex + 1) else null
    val segments = path.split("/")
    val tab = query
      ?.split("&")
      ?.firstNotNullOfOrNull { param ->
        val parts = param.split("=", limit = 2)
        if (parts.size == 2 && parts[0] == "tab") parts[1] else null
      }
    val aircraftId = segments.getOrNull(1) ?: return null
    return when (segments.getOrNull(0)) {
      "squawk" -> segments.getOrNull(2)?.let { NotificationTapTarget.Squawk(aircraftId, it) }
      "task" -> segments.getOrNull(2)?.let { NotificationTapTarget.Task(aircraftId, it) }
      "log" -> segments.getOrNull(2)?.let { NotificationTapTarget.Log(aircraftId, it) }
      "aircraft" -> NotificationTapTarget.Aircraft(aircraftId, tab)
      else -> null
    }
  }
}
