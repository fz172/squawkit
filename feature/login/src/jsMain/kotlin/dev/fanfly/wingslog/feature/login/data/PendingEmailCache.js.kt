package dev.fanfly.wingslog.feature.login.data

import kotlinx.browser.localStorage

// Backed by localStorage so any tab of the origin can read it — including the tab an email link
// opens while another tab holds the OPFS database lock (see SingleTabGate / PendingEmailCache doc).
actual object PendingEmailCache {
  private const val KEY = "wingslog_pending_signin_email"

  actual fun save(email: String) {
    localStorage.setItem(KEY, email)
  }

  actual fun clear() {
    localStorage.removeItem(KEY)
  }

  actual fun load(): String? = localStorage.getItem(KEY)?.takeIf { it.isNotBlank() }
}
