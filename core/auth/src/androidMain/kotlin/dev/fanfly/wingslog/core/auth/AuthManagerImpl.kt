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
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider

class AuthManagerImpl(
  private val context: Context,
  private val authProvider: FirebaseAuth,
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

  override suspend fun completeSignInLink(email: String, link: String): FirebaseUser? =
    emailLink.completeSignInLink(email, link)

  /**
   * Tries to sign in silently.
   * Uses filterByAuthorizedAccounts(true) to check for existing sessions.
   */
  override suspend fun trySilentLogin(): FirebaseUser? {
    if (authProvider.currentUser != null) {
      return authProvider.currentUser
    }
    try {
      val request = GetCredentialRequest.Builder()
        .addCredentialOption(
          GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
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
      // No saved credential
      logger.d { "No silent credential found: " + e.message }
    }
    return null
  }

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
   * Links a Google credential to the current anonymous user, preserving the UID. On a collision
   * (the Google account already has a Firebase account), returns the credential so the caller can
   * offer the merge path instead.
   */
  override suspend fun upgradeAnonymousAccount(): AccountUpgradeResult {
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
    private const val GOOGLE_PROVIDER_ID = "google.com"
    private const val WEB_CLIENT_ID =
      "811416892017-uul0d8vup8hie1o1172chid0q65k7vdi.apps.googleusercontent.com"
  }
}
