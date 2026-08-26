package dev.fanfly.wingslog.feature.notifications.engine

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
      // Compare against the last id handled rather than drop(1)-ing the StateFlow's current value.
      // This collector is launched at Koin init but subscribes asynchronously, so the cold-start
      // boundary (0 -> 1) can already have happened by the time it attaches; drop(1) would discard
      // exactly that emission and silently skip the most important scan of the process. Reading the
      // current value and deciding cannot miss an edge — the same reason AppForegroundObserver
      // exposes an id rather than a Flow<Unit> of events.
      var handled = 0L
      foreground.sessionId.collect { sessionId ->
        if (sessionId == 0L || sessionId == handled) return@collect
        handled = sessionId
        val result =
          runCatching { scanner().scan(ScanTrigger.SESSION_BOUNDARY) }.getOrElse { e ->
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
