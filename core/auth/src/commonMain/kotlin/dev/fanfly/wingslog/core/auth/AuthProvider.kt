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
 * Every platform offers all three. Both of the flags that used to prune this list are gone, each
 * once the limitation it described was fixed rather than left pinned to `true`:
 * `isGoogleUpgradeSupported`, because iOS's native provider signed in to Firebase itself and could
 * not hand back a credential to link (#415), and `isAppleSignInSupported`, because Android had no
 * Apple flow at all until #408 gave it Firebase's generic OAuth one.
 *
 * Still a function rather than a constant list: it is the single place that answers "what can a
 * guest upgrade to here", and the answer has been platform-dependent twice already.
 *
 * **The order matches the full-screen login page** (Google, Apple, then email) and must keep
 * matching it. The picker is the same choice offered in a different place, so a guest who has seen
 * the login screen should find the buttons where they left them.
 */
fun upgradeProvidersFor(): List<AuthProvider> = listOf(
  AuthProvider.Google,
  AuthProvider.Apple,
  AuthProvider.Email,
)
