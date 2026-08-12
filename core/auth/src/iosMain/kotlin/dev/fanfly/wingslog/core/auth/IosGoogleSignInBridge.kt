package dev.fanfly.wingslog.core.auth

import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * What the native `GIDSignIn` sheet hands back for one Google Sign-In attempt.
 *
 * Both tokens are needed: Firebase's Google credential is built from the identity token *and* the
 * access token, and the iOS binding rejects a null for either.
 */
data class GoogleSignInResult(
  val idToken: String? = null,
  val accessToken: String? = null,
  val errorMessage: String? = null,
  val cancelled: Boolean = false,
)

/**
 * Connects shared Compose login actions to the native Google presenter owned by the iOS app.
 *
 * The login screen disables its actions while a request is in flight, so a single pending
 * continuation is sufficient and also prevents multiple native account-pickers from appearing.
 *
 * Like [IosAppleSignInBridge], Swift returns the credential material rather than completing the
 * Firebase sign-in itself. That is what makes the guest upgrade possible: linking needs a real
 * `AuthCredential` on this side, and a Swift-side `Auth.auth().signIn` would have replaced the
 * anonymous user rather than linking to it.
 */
object IosGoogleSignInBridge {
  private var signInHandler: (() -> Unit)? = null
  private var pendingCompletion: Continuation<GoogleSignInResult>? = null

  fun install(signInHandler: () -> Unit) {
    this.signInHandler = signInHandler
  }

  /** Called from Swift once the `GIDSignIn` flow finishes, succeeds or fails. */
  fun complete(
    idToken: String?,
    accessToken: String?,
    errorMessage: String?,
    cancelled: Boolean,
  ) {
    val completion = pendingCompletion ?: return
    pendingCompletion = null
    completion.resume(
      GoogleSignInResult(
        idToken = idToken,
        accessToken = accessToken,
        errorMessage = errorMessage,
        cancelled = cancelled,
      )
    )
  }

  internal suspend fun signIn(): GoogleSignInResult = suspendCoroutine { continuation ->
    val signIn = signInHandler
    if (signIn == null) {
      continuation.resume(
        GoogleSignInResult(errorMessage = "Native Google Sign-In provider is not configured")
      )
      return@suspendCoroutine
    }
    if (pendingCompletion != null) {
      continuation.resume(
        GoogleSignInResult(errorMessage = "A Google Sign-In request is already in progress")
      )
      return@suspendCoroutine
    }

    pendingCompletion = continuation
    signIn()
  }
}

/**
 * Builds the Firebase credential for this authorization, or null when either token is missing.
 *
 * Unlike Apple's, this credential is replayable — Google's ID token can back a second
 * `signInWithCredential` after a failed link — which is why the collision path can return
 * [AccountUpgradeResult.CredentialInUse] instead of re-prompting.
 */
internal fun GoogleSignInResult.toCredential(): AuthCredential? {
  val id = idToken?.takeIf { it.isNotBlank() } ?: return null
  val access = accessToken?.takeIf { it.isNotBlank() } ?: return null
  return GoogleAuthProvider.credential(idToken = id, accessToken = access)
}
