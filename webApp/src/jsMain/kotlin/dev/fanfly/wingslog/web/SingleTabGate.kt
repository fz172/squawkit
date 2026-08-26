package dev.fanfly.wingslog.web

import kotlinx.browser.window

private const val LOCK_NAME = "wingslog-opfs-db"

// A single tab navigating to a new URL (e.g. a /share#… deep link) unloads the old page while the
// new one boots, and the old page's Web Lock can linger for a few ms. Retrying a handful of times
// rides that out; a genuinely-open second tab still holds the lock after all of them.
private const val RETRY_DELAY_MS = 120
private const val MAX_ATTEMPTS = 6

/**
 * Single-tab gate for the OPFS-backed SQLite database.
 *
 * OPFS sync access handles are exclusive per file, so only one browser tab can own the database at a
 * time. A second tab that opens it crashes deep inside the SQLite worker ("createSyncAccessHandle …
 * another open Access Handle"). To prevent that, we grab an exclusive Web Lock before touching the
 * database: the first tab wins the lock and runs the app via [onPrimary], holding the lock for its
 * whole lifetime; any other tab is handed [onActiveElsewhere] and never initializes storage.
 *
 * **Availability is polled, never raced against a timer.** An earlier version requested the lock
 * with an `AbortController` armed by `setTimeout(600)` and reported the abort as "open in another
 * tab". Both the timer callback and the lock grant are tasks on the same main thread, so a slow
 * startup — the dev bundle is tens of megabytes, and a cold parse alone can exceed 600 ms — let the
 * timer fire first and gated a tab that nothing was competing with. It presented as a hard "SquawkIt
 * is open in another tab" while `navigator.locks.query()` reported nothing held and the lock was
 * grantable in under a millisecond from the console: a genuinely baffling place to start debugging,
 * and it got worse simply because the bundle grew.
 *
 * `ifAvailable: true` removes the race outright — the callback is invoked with `null` when the lock
 * is taken, so the answer comes from the lock manager rather than from whichever task the event loop
 * happened to run first. How long the page took to boot no longer enters into it.
 *
 * If the Web Locks API is unavailable we fall back to [onPrimary] (the prior behavior) rather than
 * stranding the only tab.
 */
internal fun gateSingleTab(
  onPrimary: () -> Unit,
  onActiveElsewhere: () -> Unit
) {
  val hasWebLocks =
    js("typeof navigator !== 'undefined' && !!(navigator.locks)") as Boolean
  if (!hasWebLocks) {
    onPrimary()
    return
  }
  tryAcquire(
    attempt = 1,
    onPrimary = onPrimary,
    onActiveElsewhere = onActiveElsewhere
  )
}

private fun tryAcquire(
  attempt: Int,
  onPrimary: () -> Unit,
  onActiveElsewhere: () -> Unit,
) {
  // Invoked with the lock when it was free, or with null when another page holds it. Returning a
  // promise that never settles keeps a held lock for this tab's whole lifetime, so other tabs stay
  // gated until this one closes.
  val onLock: (dynamic) -> dynamic = { lock ->
    if (lock == null) {
      if (attempt < MAX_ATTEMPTS) {
        window.setTimeout(
          { tryAcquire(attempt + 1, onPrimary, onActiveElsewhere); Unit },
          RETRY_DELAY_MS,
        )
      } else {
        onActiveElsewhere()
      }
      null
    } else {
      onPrimary()
      js("new Promise(function () {})")
    }
  }

  try {
    val options: dynamic = js("({ mode: 'exclusive', ifAvailable: true })")
    val request =
      window.navigator.asDynamic().locks.request(LOCK_NAME, options, onLock)
    // Only reachable when [onPrimary] itself threw — the lock was held and then released by the
    // rejection. Distinguishing that from contention is the point: a startup crash used to be
    // indistinguishable from a second tab, and looked exactly like one.
    val onFailure: (dynamic) -> Unit = { reason ->
      console.error(
        "SquawkIt failed to start. This is NOT another tab holding the database lock — " +
          "the lock was acquired and app startup threw inside it. Real cause:",
        reason,
      )
      onActiveElsewhere()
    }
    request.then(null, onFailure)
  } catch (t: Throwable) {
    // Defensive: never leave the page blank if the lock request itself errors.
    console.error("SquawkIt single-tab gate failed; starting anyway", t)
    onPrimary()
  }
}
