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
 * Derived from capability flags rather than declared per platform, so the picker only ever offers
 * something that can actually succeed. Android omits Apple — Google is the platform's provider
 * there — and iOS omits Google until its native provider can return a credential to link with,
 * rather than showing a button that always fails.
 *
 * Email is offered everywhere a guest session exists: its link path is platform-agnostic.
 */
fun upgradeProvidersFor(
  isAppleSignInSupported: Boolean,
  isGoogleUpgradeSupported: Boolean,
): List<AuthProvider> = buildList {
  if (isAppleSignInSupported) add(AuthProvider.Apple)
  if (isGoogleUpgradeSupported) add(AuthProvider.Google)
  add(AuthProvider.Email)
}
