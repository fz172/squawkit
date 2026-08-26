package dev.fanfly.wingslog.feature.login.data

import androidx.lifecycle.ViewModel
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginViewModel(
  private val authManager: AuthManager,
  private val emailLinkStore: EmailLinkStore,
) : ViewModel() {

  private val _signInInFlight = MutableStateFlow(false)

  /**
   * True while a sign-in started *on this device* is still running.
   *
   * `AuthFlow` also advances out of the login step when Firebase reports a signed-in user, which is
   * how a sign-in completed in another browser tab gets picked up. That listener fires the moment
   * the credential is accepted — before a provider call has finished any follow-up profile work —
   * so it must not pre-empt the sign-in happening here. Sign in with Apple is the case that breaks:
   * Apple supplies the display name outside the credential, so `signInWithApple()` writes it in a
   * second round trip and the listener would otherwise read a still-empty profile and route a named
   * user to the name-entry step. See `AuthFlow`.
   */
  val signInInFlight: StateFlow<Boolean> = _signInInFlight.asStateFlow()

  suspend fun silentLogin() = authManager.trySilentLogin()

  suspend fun login() = tracked { authManager.signInWithGoogle() }

  suspend fun loginWithApple() = tracked { authManager.signInWithApple() }

  suspend fun loginAnonymously() = tracked { authManager.signInAnonymously() }

  private suspend fun <T> tracked(block: suspend () -> T): T {
    _signInInFlight.value = true
    try {
      return block()
    } finally {
      _signInInFlight.value = false
    }
  }

  /** Leg 1 — sends a sign-in link and, on success, stashes the address for completion. */
  suspend fun sendEmailLink(email: String): SendLinkResult {
    val result = authManager.sendSignInLink(email)
    if (result is SendLinkResult.Sent) {
      emailLinkStore.savePendingEmail(result.email)
    }
    return result
  }

  fun isEmailSignInLink(link: String): Boolean =
    authManager.isSignInWithEmailLink(link)

  /** The address a link was last sent to on this device, if any (null on a different device). */
  suspend fun pendingEmail(): String? = emailLinkStore.pendingEmail()

  /**
   * Leg 2 — completes sign-in from [link], using [fallbackEmail] (entered by the user) when no
   * address was stashed on this device. Returns null when the link isn't a sign-in link, no email is
   * available, or completion fails. Clears the stash on success.
   */
  suspend fun completeEmailLink(
    link: String,
    fallbackEmail: String? = null
  ): FirebaseUser? {
    if (!authManager.isSignInWithEmailLink(link)) return null
    val email =
      fallbackEmail?.takeIf { it.isNotBlank() } ?: pendingEmail() ?: return null
    val user = authManager.completeSignInLink(email, link)
    if (user != null) emailLinkStore.clear()
    return user
  }
}
