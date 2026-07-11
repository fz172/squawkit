package dev.fanfly.wingslog.web

import kotlinx.browser.window

private const val LOCK_NAME = "wingslog-opfs-db"

// How long to wait for the lock before concluding another tab really holds it. Covers the brief
// window where a single tab navigating to a new URL (e.g. a /share#… deep link) still holds its
// previous page's lock while the new page boots.
private const val LOCK_WAIT_MS = 600

/**
 * Single-tab gate for the OPFS-backed SQLite database.
 *
 * OPFS sync access handles are exclusive per file, so only one browser tab can own the database at a
 * time. A second tab that opens it crashes deep inside the SQLite worker ("createSyncAccessHandle …
 * another open Access Handle"). To prevent that, we grab an exclusive Web Lock before touching the
 * database: the first tab wins the lock and runs the app via [onPrimary], holding the lock for its
 * whole lifetime; any other tab is handed [onActiveElsewhere] and never initializes storage.
 *
 * We wait up to [LOCK_WAIT_MS] for the lock rather than checking `ifAvailable` instantly: navigating
 * a single tab to a deep link unloads the old page and boots the new one, and the old page's Web
 * Lock can linger for a few ms — an instant check would misreport that transient overlap as "open in
 * another tab" (which is why a manual refresh cleared it). A genuinely-open second tab still holds
 * the lock past the timeout and is correctly gated.
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

  // Invoked only once the exclusive lock is held. Returning a promise that never settles keeps the
  // lock for this tab's whole lifetime, so other tabs stay gated until this one closes.
  val onLock: (dynamic) -> dynamic = {
    onPrimary()
    js("new Promise(function () {})")
  }
  // The request was aborted by the timeout below — another tab genuinely holds the lock.
  val onAbort: (dynamic) -> Unit = { onActiveElsewhere() }

  try {
    val controller: dynamic = js("new AbortController()")
    window.setTimeout({ controller.abort(); Unit }, LOCK_WAIT_MS)
    val options: dynamic = js("({ mode: 'exclusive' })")
    options.signal = controller.signal
    val request = window.navigator.asDynamic().locks.request(LOCK_NAME, options, onLock)
    request.then(null, onAbort)
  } catch (t: Throwable) {
    // Defensive: never leave the page blank if the lock request itself errors.
    onPrimary()
  }
}
