package dev.fanfly.wingslog.dev.fanfly.wingslog.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class AuthManager @Inject internal constructor(@ApplicationContext private val context: Context) {
  private val credentialManager: CredentialManager =
    CredentialManager.Companion.create(context = context)
  private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

  fun getCurrentUser(): FirebaseUser? {
    return auth.currentUser
  }

  /**
   * Tries to sign in silently.
   * Uses filterByAuthorizedAccounts(true) to check for existing sessions.
   */
  suspend fun trySilentLogin(): FirebaseUser? {
    if (auth.currentUser != null) {
      return auth.currentUser
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
      Log.d(TAG, "No silent credential found: " + e.message)
    }
    return null
  }

  /**
   * Initiates the Google Sign-in flow, showing the account picker if necessary.
   * Uses filterByAuthorizedAccounts(false).
   */
  suspend fun signInWithGoogle(): FirebaseUser? {
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
      Log.d(TAG, "Google Sign-in error: " + e.message)
    }
    return null
  }


  private fun processCredential(credential: Credential): GoogleIdTokenCredential? {
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      try {
        return GoogleIdTokenCredential.Companion.createFrom(credential.data)
      } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Received an invalid google id token response", e)
      }
    } else {
      Log.w(TAG, "Unexpected type of credential: " + credential.type)
    }
    return null
  }

  private suspend fun signInToFirebase(credential: GoogleIdTokenCredential): FirebaseUser? {
    return try {
      val firebaseCredential =
        GoogleAuthProvider.getCredential(credential.idToken, null)
      auth.signInWithCredential(firebaseCredential).await()
      auth.currentUser
    } catch (e: Exception) {
      Log.e(TAG, "Firebase sign-in failed", e)
      null
    }
  }


  suspend fun logOut() {
    try {
      auth.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Log.e(TAG, "Error logging out: " + e.message)
    }
  }


  companion object {

    private const val TAG = "AuthManager"
    private const val WEB_CLIENT_ID =
      "811416892017-uul0d8vup8hie1o1172chid0q65k7vdi.apps.googleusercontent.com"
  }
}