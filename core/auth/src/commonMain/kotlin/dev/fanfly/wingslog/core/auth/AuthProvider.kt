package dev.fanfly.wingslog.core.auth

/**
 * A sign-in provider a guest can attach their session to.
 *
 * Which of these a platform actually offers is a capability question, not a preference — see
 * [upgradeProvidersFor]. The enum itself is platform-agnostic so the shared upgrade UI can render a
 * list without knowing where it is running.
 */
enum class AuthProvider {
  Google,
  Apple,

  /**
   * Passwordless email link. Unlike the other two this cannot complete in one call: leg 1 sends the
   * link and the app is left behind, leg 2 resumes when the link reopens it. See
   * [AuthManager.sendSignInLink] and [AuthManager.completeUpgradeWithEmailLink].
   */
  Email,
}

/**
 * The providers a guest on this platform can upgrade to, in the order they should be offered.
 *
 * Derived from capability flags rather than declared per platform, so it cannot drift from what the
 * login screen shows: Apple appears exactly where the Apple button does, and Google is offered
 * everywhere a guest session can exist at all. Android deliberately omits Apple — Google is the
 * platform's provider there (see `AppCapability.isAppleSignInSupported`).
 */
fun upgradeProvidersFor(isAppleSignInSupported: Boolean): List<AuthProvider> =
  buildList {
    if (isAppleSignInSupported) add(AuthProvider.Apple)
    add(AuthProvider.Google)
    add(AuthProvider.Email)
  }
