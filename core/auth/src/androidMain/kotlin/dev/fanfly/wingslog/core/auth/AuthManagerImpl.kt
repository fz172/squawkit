package dev.fanfly.wingslog.core.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import co.touchlab.kermit.Logger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider

class AuthManagerImpl(
  private val context: Context,
  private val authProvider: FirebaseAuth,
) : AuthManager {
  private val credentialManager: CredentialManager =
    CredentialManager.create(context = context)

  override fun getCurrentUser(): FirebaseUser? {
    return authProvider.currentUser
  }

  /**
   * Tries to sign in silently.
   * Uses filterByAuthorizedAccounts(true) to check for existing sessions.
   */
  override suspend fun trySilentLogin(): FirebaseUser? {
    if (authProvider.currentUser != null) {
      return authProvider.currentUser
    }
    try {
      val request = GetCredentialRequest.Builder().addCredentialOption(
        GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(true)
          .setServerClientId(WEB_CLIENT_ID).build()
      ).build()
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
      val request = GetCredentialRequest.Builder().addCredentialOption(
        GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false) // Show account picker
          .setServerClientId(WEB_CLIENT_ID).build()
      ).build()
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
      authProvider.signInWithCredential(firebaseCredential)
      authProvider.currentUser
    } catch (e: Exception) {
      logger.e(e) { "Firebase sign-in failed" }
      null
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

  companion object {
    private val logger = Logger.withTag("AuthManagerImpl")
    private const val WEB_CLIENT_ID =
      "811416892017-uul0d8vup8hie1o1172chid0q65k7vdi.apps.googleusercontent.com"
  }
}
