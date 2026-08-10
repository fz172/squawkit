package dev.fanfly.wingslog.core.auth

import dev.gitlive.firebase.auth.OAuthCredential
import dev.gitlive.firebase.auth.OAuthProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * What the native `ASAuthorization` sheet hands back for one Sign in with Apple attempt.
 *
 * [rawNonce] is the un-hashed nonce; Swift sends its SHA-256 to Apple and keeps this one for the
 * credential exchange, which is what makes the identity token non-replayable.
 *
 * [fullName] is populated **only on the very first authorization** of this app for the Apple ID —
 * every later sign-in leaves it null, and Firebase never derives `displayName` from Apple. See
 * `AuthManagerImpl.signInWithApple`.
 */
data class AppleSignInResult(
  val idToken: String? = null,
  val rawNonce: String? = null,
  val fullName: String? = null,
  val email: String? = null,
  val errorMessage: String? = null,
  val cancelled: Boolean = false,
)

/**
 * Connects the shared Compose login actions to the native Sign in with Apple presenter owned by the
 * iOS app, mirroring [IosGoogleSignInBridge]'s single-pending-continuation shape: the login screen
 * disables its actions while a request is in flight, so one continuation is sufficient and it also
 * prevents a second system sheet stacking on the first.
 *
 * Unlike the Google bridge — where Swift completes the Firebase sign-in itself and returns only an
 * error string — this one returns the raw credential material and lets Kotlin build the credential.
 * That is deliberate: the account-upgrade collision path has to hand a real `AuthCredential` to
 * [AuthManager.signInToExistingAccount], which Swift-side sign-in could not provide.
 */
object IosAppleSignInBridge {
  private var signInHandler: (() -> Unit)? = null
  private var pendingCompletion: Continuation<AppleSignInResult>? = null

  fun install(signInHandler: () -> Unit) {
    this.signInHandler = signInHandler
  }

  /** Called from Swift once the `ASAuthorizationController` finishes, succeeds or fails. */
  fun complete(
    idToken: String?,
    rawNonce: String?,
    fullName: String?,
    email: String?,
    errorMessage: String?,
    cancelled: Boolean,
  ) {
    val completion = pendingCompletion ?: return
    pendingCompletion = null
    completion.resume(
      AppleSignInResult(
        idToken = idToken,
        rawNonce = rawNonce,
        fullName = fullName,
        email = email,
        errorMessage = errorMessage,
        cancelled = cancelled,
      )
    )
  }

  internal suspend fun signIn(): AppleSignInResult = suspendCoroutine { continuation ->
    val signIn = signInHandler
    if (signIn == null) {
      continuation.resume(
        AppleSignInResult(errorMessage = "Native Sign in with Apple provider is not configured")
      )
      return@suspendCoroutine
    }
    if (pendingCompletion != null) {
      continuation.resume(
        AppleSignInResult(errorMessage = "A Sign in with Apple request is already in progress")
      )
      return@suspendCoroutine
    }

    pendingCompletion = continuation
    signIn()
  }
}

/**
 * Builds the Firebase credential for this authorization, or null when Apple returned no identity
 * token. The raw nonce must travel with it — Firebase re-hashes it and compares against the `nonce`
 * claim in the token, rejecting the credential if they disagree.
 */
internal fun AppleSignInResult.toCredential(): OAuthCredential? {
  val token = idToken?.takeIf { it.isNotBlank() } ?: return null
  return OAuthProvider.credential(
    providerId = APPLE_PROVIDER_ID,
    idToken = token,
    rawNonce = rawNonce,
  )
}
