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
 * something that can actually succeed. Every platform now offers all three: Android gained Apple in
 * #408, via Firebase's generic OAuth flow rather than a native SDK, so [isAppleSignInSupported] is
 * true everywhere today. The parameter stays because the *reason* it could be false has not gone
 * away — it is a per-platform capability, not a constant.
 *
 * Google and Email are offered everywhere a guest session exists. Google used to be gated on its
 * own `isGoogleUpgradeSupported` flag, because iOS's native provider signed in to Firebase itself
 * and so could not hand back a credential to link; it now returns the token pair like the Apple
 * one does (#415), and the flag is gone rather than pinned to `true`.
 *
 * **The order matches the full-screen login page** (Google, Apple, then email) and must keep
 * matching it. The picker is the same choice offered in a different place, so a guest who has seen
 * the login screen should find the buttons where they left them.
 */
fun upgradeProvidersFor(
  isAppleSignInSupported: Boolean,
): List<AuthProvider> = buildList {
  add(AuthProvider.Google)
  if (isAppleSignInSupported) add(AuthProvider.Apple)
  add(AuthProvider.Email)
}
