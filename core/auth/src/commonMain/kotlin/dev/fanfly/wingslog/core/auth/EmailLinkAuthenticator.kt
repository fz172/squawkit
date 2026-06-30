package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.ActionCodeSettings
import dev.gitlive.firebase.auth.AndroidPackageName
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser

/**
 * Platform-agnostic implementation of passwordless email-link sign-in, shared by every
 * [AuthManager] implementation. The GitLive SDK surface used here (`sendSignInLinkToEmail`,
 * `isSignInWithEmailLink`, `signInWithEmailLink`) is available identically on Android, iOS, and JS,
 * so the three [AuthManagerImpl]s simply delegate to one instance of this class.
 *
 * Design: docs/account/email_link_signin_design.html.
 */
class EmailLinkAuthenticator(
  private val auth: FirebaseAuth,
) {

  /**
   * Leg 1 — emails a one-time sign-in link to [email]. The caller is responsible for stashing the
   * address locally so leg 2 can complete (see the feature/login EmailLinkStore).
   */
  suspend fun sendSignInLink(email: String): SendLinkResult {
    val trimmed = email.trim()
    if (!isLikelyEmail(trimmed)) return SendLinkResult.InvalidEmail
    return try {
      auth.sendSignInLinkToEmail(trimmed, actionCodeSettings())
      SendLinkResult.Sent(trimmed)
    } catch (e: Exception) {
      logger.e(e) { "sendSignInLinkToEmail failed" }
      SendLinkResult.Failed(e.message ?: "Could not send sign-in link")
    }
  }

  /** Cheap check: is [link] a Firebase email sign-in link? Lets hosts ignore unrelated deep links. */
  fun isSignInWithEmailLink(link: String): Boolean =
    try {
      auth.isSignInWithEmailLink(link)
    } catch (e: Exception) {
      logger.d { "isSignInWithEmailLink threw: ${e.message}" }
      false
    }

  /**
   * Leg 2 — exchanges [link] (plus the [email] it was issued for) for a signed-in [FirebaseUser].
   * Returns null when [link] is not a sign-in link or completion fails.
   */
  suspend fun completeSignInLink(email: String, link: String): FirebaseUser? {
    val trimmed = email.trim()
    if (trimmed.isEmpty() || !isSignInWithEmailLink(link)) return null
    return try {
      auth.signInWithEmailLink(trimmed, link)
      auth.currentUser
    } catch (e: Exception) {
      logger.e(e) { "signInWithEmailLink failed" }
      null
    }
  }

  private companion object {
    private val logger = Logger.withTag("EmailLinkAuthenticator")

    // Returned-to URL hosted on our Firebase Hosting domain. Android App Links / iOS Universal
    // Links route the tap back into the app; the browser is the graceful fallback. Firebase
    // Dynamic Links is end-of-life, so dynamicLinkDomain is intentionally left unset.
    private const val CONTINUE_URL = "https://squawkit.fanfly.dev/finishSignIn"
    private const val APP_ID = "dev.fanfly.wingslog"

    fun actionCodeSettings(): ActionCodeSettings = ActionCodeSettings(
      url = CONTINUE_URL,
      androidPackageName = AndroidPackageName(
        packageName = APP_ID,
        installIfNotAvailable = true,
        minimumVersion = null,
      ),
      iOSBundleId = APP_ID,
      canHandleCodeInApp = true,
    )

    // Deliberately permissive: Firebase validates for real on send. We only guard against obvious
    // empties / missing "@" so the UI can show inline validation without a network round-trip.
    fun isLikelyEmail(value: String): Boolean {
      val at = value.indexOf('@')
      if (at <= 0 || at != value.lastIndexOf('@')) return false
      val domain = value.substring(at + 1)
      return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
    }
  }
}
