package dev.fanfly.wingslog.feature.login.data

import androidx.lifecycle.ViewModel
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.gitlive.firebase.auth.FirebaseUser

class LoginViewModel(
  private val authManager: AuthManager,
  private val emailLinkStore: EmailLinkStore,
) : ViewModel() {

  suspend fun silentLogin() = authManager.trySilentLogin()

  suspend fun login() = authManager.signInWithGoogle()

  suspend fun loginAnonymously() = authManager.signInAnonymously()

  /** Leg 1 — sends a sign-in link and, on success, stashes the address for completion. */
  suspend fun sendEmailLink(email: String): SendLinkResult {
    val result = authManager.sendSignInLink(email)
    if (result is SendLinkResult.Sent) {
      emailLinkStore.savePendingEmail(result.email)
    }
    return result
  }

  fun isEmailSignInLink(link: String): Boolean = authManager.isSignInWithEmailLink(link)

  /** The address a link was last sent to on this device, if any (null on a different device). */
  suspend fun pendingEmail(): String? = emailLinkStore.pendingEmail()

  /**
   * Leg 2 — completes sign-in from [link], using [fallbackEmail] (entered by the user) when no
   * address was stashed on this device. Returns null when the link isn't a sign-in link, no email is
   * available, or completion fails. Clears the stash on success.
   */
  suspend fun completeEmailLink(link: String, fallbackEmail: String? = null): FirebaseUser? {
    if (!authManager.isSignInWithEmailLink(link)) return null
    val email = fallbackEmail?.takeIf { it.isNotBlank() } ?: pendingEmail() ?: return null
    val user = authManager.completeSignInLink(email, link)
    if (user != null) emailLinkStore.clear()
    return user
  }
}
