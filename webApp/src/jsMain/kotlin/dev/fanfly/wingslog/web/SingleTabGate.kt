package dev.fanfly.wingslog.web

import kotlinx.browser.window

private const val LOCK_NAME = "wingslog-opfs-db"

/**
 * Single-tab gate for the OPFS-backed SQLite database.
 *
 * OPFS sync access handles are exclusive per file, so only one browser tab can own the database at a
 * time. A second tab that opens it crashes deep inside the SQLite worker ("createSyncAccessHandle …
 * another open Access Handle"). To prevent that, we grab an exclusive Web Lock before touching the
 * database: the first tab wins the lock and runs the app via [onPrimary], holding the lock for its
 * whole lifetime; any other tab is handed [onActiveElsewhere] and never initializes storage.
 *
 * If the Web Locks API is unavailable we fall back to [onPrimary] (the prior behavior) rather than
 * stranding the only tab.
 */
internal fun gateSingleTab(onPrimary: () -> Unit, onActiveElsewhere: () -> Unit) {
  val hasWebLocks = js("typeof navigator !== 'undefined' && !!(navigator.locks)") as Boolean
  if (!hasWebLocks) {
    onPrimary()
    return
  }

  // `ifAvailable: true` makes the callback run immediately — with the lock if it's free, or with
  // null if another tab already holds it. Returning a promise that never settles keeps the lock for
  // this tab's whole lifetime, so other tabs stay gated until this one closes.
  val onLock: (dynamic) -> dynamic = { lock ->
    if (lock != null) {
      onPrimary()
      js("new Promise(function () {})")
    } else {
      onActiveElsewhere()
      null
    }
  }

  try {
    val options: dynamic = js("({ mode: 'exclusive', ifAvailable: true })")
    window.navigator.asDynamic().locks.request(LOCK_NAME, options, onLock)
  } catch (t: Throwable) {
    // Defensive: never leave the page blank if the lock request itself errors.
    onPrimary()
  }
}
