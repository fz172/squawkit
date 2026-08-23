package dev.fanfly.wingslog.feature.notifications.engine

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Runs an urgency scan whenever the user comes back to the app after being away (design §6.6).
 *
 * This is the trigger that actually carries an active pilot. Android's `PeriodicWorkRequest` can be
 * killed by an OEM battery manager and iOS's `BGAppRefreshTask` is opportunistic to the point of
 * going quiet for a day — on both, the honest answer to "how often does the scan really run" is
 * "about as often as you open the app".
 *
 * **It collects [AppForegroundObserver.sessionId] rather than adding a foreground event stream.**
 * A new session id already means "came back after 30 minutes away", is already debounced by that
 * threshold, is already driven on all three hosts from one `LifecycleResumeEffect` at the shell
 * root, and is already unit-testable with a fake clock. The alternative — widening
 * `AppForegroundObserver` with a second signal for a second consumer — is what its own doc comment
 * warns against. PRD §9.1 calls that class a foreground observer; it is a session-boundary counter,
 * and this is written against what it actually is.
 *
 * The 2h debounce lives in [UrgencyScanner], not here, because it needs the signed-in uid to read
 * [LastScanStore]. This class only decides *when to ask*.
 *
 * [scanner] is a provider rather than the instance: this is built eagerly at Koin init, and
 * resolving [UrgencyScanner] there would construct `FirebaseAuth` during startup, which NPEs on
 * iOS. Same reason `HttpsAttachmentBroker` takes a `functionsProvider`.
 */
class SessionBoundaryScanTrigger(
  private val foreground: AppForegroundObserver,
  private val scanner: () -> UrgencyScanner,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

  private var job: Job? = null

  /** Idempotent — a second call while already collecting is a no-op, not a second collector. */
  fun start() {
    if (job?.isActive == true) return
    job = scope.launch {
      // drop(1) skips the StateFlow's current value: at Koin init that is 0 ("never foregrounded"),
      // and on the rare restart where a session is already open it is a boundary that has already
      // been handled. Only transitions from here on are real boundaries.
      foreground.sessionId
        .drop(1)
        .collect { sessionId ->
          val result =
            runCatching { scanner().scan(ScanTrigger.SESSION_BOUNDARY) }
              .getOrElse { e ->
                // A failed scan must not tear down the collector — that would silently disable the
                // trigger for the rest of the process.
                log.w(e) { "session-boundary scan failed (session $sessionId)" }
                return@collect
              }
          log.d { "session-boundary scan (session $sessionId): $result" }
        }
    }
  }

  private companion object {
    val log = Logger.withTag("SessionBoundaryScanTrigger")
  }
}
