package dev.fanfly.wingslog.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import co.touchlab.kermit.Logger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.gms.tasks.Task
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.android
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.firebase.auth.OAuthProvider as AndroidOAuthProvider

/**
 * Awaits a Play Services [Task], failing with whatever the task failed with.
 *
 * Hand-rolled rather than pulling in `kotlinx-coroutines-play-services` for the two Apple call
 * sites — the rest of this class goes through GitLive, which already returns suspend functions.
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
  addOnSuccessListener { continuation.resume(it) }
  addOnFailureListener { continuation.resumeWithException(it) }
  addOnCanceledListener { continuation.cancel() }
}

class AuthManagerImpl(
  private val context: Context,
  private val authProvider: FirebaseAuth,
  private val activityProvider: CurrentActivityProvider,
) : AuthManager {
  private val credentialManager: CredentialManager =
    CredentialManager.create(context = context)
  private val emailLink = EmailLinkAuthenticator(authProvider)

  override fun getCurrentUser(): FirebaseUser? {
    return authProvider.currentUser
  }

  override suspend fun sendSignInLink(email: String): SendLinkResult =
    emailLink.sendSignInLink(email)

  override fun isSignInWithEmailLink(link: String): Boolean =
    emailLink.isSignInWithEmailLink(link)

  override suspend fun completeSignInLink(
    email: String,
    link: String
  ): FirebaseUser? =
    emailLink.completeSignInLink(email, link)

  /**
   * Returns the already-signed-in user, or null. Only consults the persisted Firebase session — it
   * deliberately does NOT invoke Credential Manager, because even
   * `setFilterByAuthorizedAccounts(true)` surfaces the Google account-picker bottom sheet when
   * authorized accounts exist. That made the picker pop up unprompted on the login screen; instead we
   * let the user choose a sign-in method and only show Google's UI when they tap "Log in with Google"
   * (see [signInWithGoogle]). Matches the iOS implementation, which also just returns `currentUser`.
   */
  override suspend fun trySilentLogin(): FirebaseUser? =
    authProvider.currentUser

  /**
   * Signs in anonymously using Firebase Authentication.
   * Does not interfere with [trySilentLogin] — if a user is already signed in
   * (including anonymously), this is a no-op and returns the current user.
   */
  override suspend fun signInAnonymously(): FirebaseUser? {
    if (authProvider.currentUser != null) {
      return authProvider.currentUser
    }
    return try {
      authProvider.signInAnonymously()
      authProvider.currentUser
    } catch (e: Exception) {
      logger.e(e) { "Anonymous sign-in failed" }
      null
    }
  }

  /**
   * Initiates the Google Sign-in flow, showing the account picker if necessary.
   * Uses filterByAuthorizedAccounts(false).
   */
  override suspend fun signInWithGoogle(): FirebaseUser? {
    try {
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(
          GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Show account picker
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        )
        .build()
      val result = credentialManager.getCredential(context, request)
      val googleIdTokenCredential = processCredential(result.credential)
      if (googleIdTokenCredential != null) {
        return signInToFirebase(googleIdTokenCredential)
      }
    } catch (e: Exception) {
      // User canceled or other error
      logger.d { "Google Sign-in error: " + e.message }
    }
    return null
  }

  /**
   * Sign in with Apple, via Firebase's generic OAuth flow in a Custom Tab (#408).
   *
   * Unlike iOS there is no native Apple SDK here, so there is no identity token to build a
   * credential from: Firebase drives the whole exchange and hands back an `AuthResult`. That is why
   * this reaches through GitLive to the native SDK — `startActivityForSignInWithProvider` has no
   * GitLive wrapper — and why it needs an Activity rather than a Context.
   *
   * Apple's name is not captured here the way [signInWithApple] does on iOS. Apple returns it only
   * on the first authorization, and through this flow it arrives (if at all) in
   * `additionalUserInfo`, not on the user. The onboarding name step is what guarantees the profile
   * is populated on this platform — see `NameEntryScreen` and the parity matrix.
   */
  override suspend fun signInWithApple(): FirebaseUser? {
    val activity = activityProvider.current() ?: run {
      logger.w { "Sign in with Apple: no foreground activity to present from" }
      return null
    }

    return try {
      authProvider.android
        .startActivityForSignInWithProvider(activity, appleProvider())
        .awaitResult()
      // Read the user back through GitLive rather than wrapping the native one: its FirebaseUser
      // constructor is internal, and the sign-in has already set currentUser by this point.
      authProvider.currentUser.also { user ->
        if (user == null) logger.w { "Sign in with Apple completed without a Firebase user" }
      }
    } catch (e: Exception) {
      logger.e(e) { "Sign in with Apple failed" }
      null
    }
  }

  /**
   * `email` and `name` are requested because Apple only ever returns the user's name on the very
   * first authorization, and only when asked for.
   */
  private fun appleProvider(): AndroidOAuthProvider =
    AndroidOAuthProvider.newBuilder(APPLE_PROVIDER_ID)
      .setScopes(listOf("email", "name"))
      .build()

  private fun processCredential(credential: Credential): GoogleIdTokenCredential? {
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      try {
        return GoogleIdTokenCredential.createFrom(credential.data)
      } catch (e: GoogleIdTokenParsingException) {
        logger.e(e) { "Received an invalid google id token response" }
      }
    } else {
      logger.w { "Unexpected type of credential: " + credential.type }
    }
    return null
  }

  private suspend fun signInToFirebase(credential: GoogleIdTokenCredential): FirebaseUser? {
    return try {
      val firebaseCredential =
        GoogleAuthProvider.credential(credential.idToken, null)
      val result = authProvider.signInWithCredential(firebaseCredential)
      val user = result.user ?: authProvider.currentUser
      user?.syncProfileFromProvider(
        fallbackName = credential.displayName,
        fallbackPhotoUrl = credential.profilePictureUri?.toString(),
      )
    } catch (e: Exception) {
      logger.e(e) { "Firebase sign-in failed" }
      null
    }
  }

  /**
   * Links [provider] to the current anonymous user, preserving the UID. On a collision (the chosen
   * account already exists), returns the credential so the caller can offer the merge path instead.
   *
   * Email cannot complete in one call, so it is rejected here rather than silently doing something
   * else. Apple is offered on this platform as of #408 and links like Google does, though its
   * collision path differs — see [upgradeWithApple].
   */
  override suspend fun upgradeAnonymousAccount(
    provider: AuthProvider,
  ): AccountUpgradeResult = when (provider) {
    AuthProvider.Google -> upgradeWithGoogle()
    AuthProvider.Apple -> upgradeWithApple()

    AuthProvider.Email -> AccountUpgradeResult.Failed(
      "Email upgrade completes through completeUpgradeWithEmailLink"
    )
  }

  /**
   * Links Sign in with Apple to the current anonymous user, preserving the UID (#408).
   *
   * The collision path returns [AccountUpgradeResult.ReauthRequiredToMerge] rather than
   * [AccountUpgradeResult.CredentialInUse], matching iOS: Firebase drives this exchange itself and
   * never hands us a credential, so there is nothing to replay into the merge — it has to run the
   * provider flow again. That is the opposite of Google here, whose ID token we hold and can reuse.
   */
  private suspend fun upgradeWithApple(): AccountUpgradeResult {
    val current = authProvider.currentUser
      ?: return AccountUpgradeResult.Failed("No signed-in user to upgrade")
    val activity = activityProvider.current()
      ?: return AccountUpgradeResult.Failed("No foreground activity to present Apple sign-in")

    return try {
      current.android
        .startActivityForLinkWithProvider(activity, appleProvider())
        .awaitResult()
      AccountUpgradeResult.Linked(
        authProvider.currentUser
          ?: return AccountUpgradeResult.Failed("Linking returned no user")
      )
    } catch (e: FirebaseAuthUserCollisionException) {
      logger.i { "Apple account already in use; merge needs a fresh authorization" }
      AccountUpgradeResult.ReauthRequiredToMerge(AuthProvider.Apple)
    } catch (e: Exception) {
      logger.e(e) { "Account upgrade: Apple linking failed" }
      AccountUpgradeResult.Failed(e.message ?: "Linking failed")
    }
  }

  override suspend fun completeUpgradeWithEmailLink(
    email: String,
    link: String,
  ): AccountUpgradeResult = emailLink.linkToCurrentUser(email, link)

  private suspend fun upgradeWithGoogle(): AccountUpgradeResult {
    val current = authProvider.currentUser
      ?: return AccountUpgradeResult.Failed("No signed-in user to upgrade")
    val googleCredential = try {
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(
          GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Show account picker
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        )
        .build()
      val result = credentialManager.getCredential(context, request)
      processCredential(result.credential)
        ?: return AccountUpgradeResult.Failed("Could not read Google credential")
    } catch (e: GetCredentialCancellationException) {
      logger.d { "Account upgrade cancelled by user" }
      return AccountUpgradeResult.Cancelled
    } catch (e: Exception) {
      logger.e(e) { "Account upgrade: credential retrieval failed" }
      return AccountUpgradeResult.Failed(e.message ?: "Sign-in failed")
    }

    return try {
      val firebaseCredential =
        GoogleAuthProvider.credential(googleCredential.idToken, null)
      val result = current.linkWithCredential(firebaseCredential)
      val linkedUser = result.user ?: authProvider.currentUser ?: current
      val user = linkedUser.syncProfileFromProvider(
        fallbackName = googleCredential.displayName,
        fallbackPhotoUrl = googleCredential.profilePictureUri?.toString(),
      )
      AccountUpgradeResult.Linked(user)
    } catch (e: FirebaseAuthUserCollisionException) {
      logger.i { "Google account already in use; offering merge" }
      val firebaseCredential =
        GoogleAuthProvider.credential(googleCredential.idToken, null)
      AccountUpgradeResult.CredentialInUse(firebaseCredential)
    } catch (e: Exception) {
      logger.e(e) { "Account upgrade: linking failed" }
      AccountUpgradeResult.Failed(e.message ?: "Linking failed")
    }
  }

  /**
   * Runs the Apple flow a second time and signs in to the account that already owns that Apple ID.
   *
   * Only Apple reaches this on Android. A Google credential can be replayed, so its collision path
   * returns [AccountUpgradeResult.CredentialInUse] and the merge reuses it with no second picker;
   * Apple's exchange is owned by Firebase and yields no credential to replay, so the only way
   * through is to authorize again. The caller has already told the user why — see
   * `AccountUpgradeViewModel.askToMerge`.
   */
  override suspend fun mergeIntoExistingAccount(
    provider: AuthProvider,
  ): AccountUpgradeResult {
    if (provider != AuthProvider.Apple) {
      logger.w { "mergeIntoExistingAccount($provider) is not used on Android" }
      return AccountUpgradeResult.Failed("Re-authorization is only needed for Apple on Android")
    }
    val activity = activityProvider.current()
      ?: return AccountUpgradeResult.Failed("No foreground activity to present Apple sign-in")

    return try {
      authProvider.android
        .startActivityForSignInWithProvider(activity, appleProvider())
        .awaitResult()
      val user = authProvider.currentUser
        ?: return AccountUpgradeResult.Failed("Sign-in returned no user")
      if (user.isAnonymous) {
        return AccountUpgradeResult.Failed("Sign-in did not switch to the permanent account")
      }
      AccountUpgradeResult.Linked(user.syncProfileFromProvider())
    } catch (e: Exception) {
      logger.e(e) { "Merge: second Apple authorization failed" }
      AccountUpgradeResult.Failed(e.message ?: "Sign-in failed")
    }
  }

  override suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult {
    return try {
      val result = authProvider.signInWithCredential(credential)
      val signedInUser = result.user ?: authProvider.currentUser
      ?: return AccountUpgradeResult.Failed("Sign-in returned no user")
      val user = signedInUser.syncProfileFromProvider()
      if (user.isAnonymous) {
        return AccountUpgradeResult.Failed("Sign-in did not switch to the permanent account")
      }
      AccountUpgradeResult.Linked(user)
    } catch (e: Exception) {
      logger.e(e) { "Sign-in to existing account failed" }
      AccountUpgradeResult.Failed(e.message ?: "Sign-in failed")
    }
  }

  override suspend fun updateDisplayName(name: String) {
    val user = authProvider.currentUser ?: return
    if (name.isBlank() || name == user.displayName) return
    try {
      user.updateProfile(displayName = name, photoUrl = user.photoURL)
      user.reload()
    } catch (e: Exception) {
      // Best-effort: the in-app name is already correct everywhere the client reads it. This only
      // keeps the token in step for the server-side reads.
      logger.w(e) { "Could not push display name to the auth profile" }
    }
  }

  override suspend fun logOut() {
    try {
      authProvider.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      logger.e(e) { "Error logging out: " }
    }
  }


  private suspend fun FirebaseUser.syncProfileFromProvider(
    fallbackName: String? = null,
    fallbackPhotoUrl: String? = null,
  ): FirebaseUser {
    reload()
    val googleProfile =
      providerData.firstOrNull { it.providerId == GOOGLE_PROVIDER_ID }
    val accountName = googleProfile?.displayName?.takeIf { it.isNotBlank() }
      ?: fallbackName?.takeIf { it.isNotBlank() }
    val accountPhotoUrl = googleProfile?.photoURL?.takeIf { it.isNotBlank() }
      ?: fallbackPhotoUrl?.takeIf { it.isNotBlank() }

    val shouldUpdateName = accountName != null && accountName != displayName
    val shouldUpdatePhoto =
      accountPhotoUrl != null && accountPhotoUrl != photoURL
    if (shouldUpdateName || shouldUpdatePhoto) {
      updateProfile(
        displayName = accountName ?: displayName,
        photoUrl = accountPhotoUrl ?: photoURL,
      )
      reload()
    }
    return this
  }

  companion object {
    private val logger = Logger.withTag("AuthManagerImpl")
    private const val WEB_CLIENT_ID =
      "811416892017-uul0d8vup8hie1o1172chid0q65k7vdi.apps.googleusercontent.com"
  }
}
