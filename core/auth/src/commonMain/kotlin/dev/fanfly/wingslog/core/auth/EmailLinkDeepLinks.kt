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
 * Two consumers observe [pendingLink], and which one applies depends on who is signed in:
 *  - `AuthFlow` routes it to the Email Sign-In screen when nobody is signed in.
 *  - `AccountUpgradeViewModel` completes it as a *link* when the current user is a guest, so the
 *    anonymous UID is preserved instead of being replaced by a fresh sign-in.
 *
 * Both live in `feature/login`, but this stays in `core:auth` because the hosts that deliver links
 * are the ones that cannot reach it otherwise: `MainActivity` (app) and `MainEntry` (composeApp)
 * both push URLs in here without depending on any feature module. See
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
