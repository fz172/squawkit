package dev.fanfly.wingslog.feature.login

import dev.fanfly.wingslog.feature.login.EmailLinkDeepLinks.pendingLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-agnostic channel for delivering an inbound email sign-in link into the running auth flow.
 *
 * Each host pushes the URL that opened the app:
 *  - Android — `MainActivity` from the launch intent and `onNewIntent`,
 *  - iOS — `MainEntry.handleIncomingUrl` forwarded from `onOpenURL` / Universal Links,
 *  - Web — `WebApp` from `window.location.href` on startup.
 *
 * [AuthFlow] observes [pendingLink] and routes a sign-in link to the Email Sign-In screen, which
 * completes leg 2 (mirrors the IosGoogleSignInBridge pattern). See
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
