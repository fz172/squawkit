package dev.fanfly.wingslog.core.auth

import android.content.Context
import co.touchlab.kermit.Logger
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider

class GitLiveAuthManagerImpl(
  private val context: Context,
  private val authProvider: FirebaseAuth,
) : GitLiveAuthManager {
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
      Logger.d(TAG) { "No silent credential found: " + e.message }
    }
    return null
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
      // User cancelled or other error
      Logger.d(TAG) { "Google Sign-in error: " + e.message }
    }
    return null
  }

  private fun processCredential(credential: Credential): GoogleIdTokenCredential? {
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      try {
        return GoogleIdTokenCredential.createFrom(credential.data)
      } catch (e: GoogleIdTokenParsingException) {
        Logger.e(TAG, e) { "Received an invalid google id token response" }
      }
    } else {
      Logger.w(TAG) { "Unexpected type of credential: " + credential.type }
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
      Logger.e(TAG, e) { "Firebase sign-in failed" }
      null
    }
  }

  override suspend fun logOut() {
    try {
      authProvider.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Logger.e(TAG) { "Error logging out: " + e.message }
    }
  }

  companion object {
    private const val TAG = "GitLiveAuthManagerImpl"
    private const val WEB_CLIENT_ID =
      "811416892017-uul0d8vup8hie1o1172chid0q65k7vdi.apps.googleusercontent.com"
  }
}
