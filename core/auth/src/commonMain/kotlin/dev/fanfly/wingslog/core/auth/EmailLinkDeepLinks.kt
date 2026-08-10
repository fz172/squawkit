package dev.fanfly.wingslog.core.auth

import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks.pendingLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-agnostic channel for delivering an inbound email sign-in link into the running app.
 *
 * Each host pushes the URL that opened the app:
 *  - Android — `MainActivity` from the launch intent and `onNewIntent`,
 *  - iOS — `MainEntry.handleIncomingUrl` forwarded from `onOpenURL` / Universal Links,
 *  - Web — `WebApp` from `window.location.href` on startup.
 *
 * Two flows observe [pendingLink], and which one applies depends on who is signed in:
 *  - `AuthFlow` (feature/login) routes it to the Email Sign-In screen when nobody is signed in.
 *  - `AccountUpgradeViewModel` (feature/settings) completes it as a *link* when the current user is
 *    a guest, so the anonymous UID is preserved instead of being replaced by a fresh sign-in.
 *
 * That second consumer is why this lives in `core:auth` rather than `feature/login`: feature modules
 * do not depend on each other, and settings cannot see login. See
 * docs/account/email_link_signin_design.html.
 */
object EmailLinkDeepLinks {
  private val _pendingLink = MutableStateFlow<String?>(null)
  val pendingLink: StateFlow<String?> = _pendingLink.asStateFlow()

  fun deliver(url: String) {
    _pendingLink.value = url
  }

  /** Called once a link has been handled so it isn't re-processed. */
  fun consume() {
    _pendingLink.value = null
  }
}
