package dev.fanfly.wingslog.login.data

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class AuthManager(private val context: Context) {
  private val credentialManager: CredentialManager = CredentialManager.create(context = context)

  @Volatile
  private var credential: GoogleIdTokenCredential? = null

  private val credentialLock = Any()

  fun getCredential(): GoogleIdTokenCredential? {
    synchronized(credentialLock) {
      return credential
    }
  }

  suspend fun login(): GoogleIdTokenCredential? {
    synchronized(credentialLock) {
      if (credential != null) {
        return credential
      }
    }

    try {
      val request = GetCredentialRequest.Builder().addCredentialOption(
        GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(true)
          .setServerClientId(WEB_CLIENT_ID).build()
      ).build()
      val result = credentialManager.getCredential(context, request)
      synchronized(credentialLock) {
        credential = processCredential(result.credential)
      }
    } catch (e: Exception) {
      // No saved credential, show login button
      Log.d(TAG, "Error: " + e.message)
    }
    return credential
  }


  private fun processCredential(credential: Credential): GoogleIdTokenCredential? {
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      try {
        return GoogleIdTokenCredential.createFrom(credential.data)
      } catch (e: GoogleIdTokenParsingException) {
        Log.e(TAG, "Received an invalid google id token response", e)
      }
    } else {
      Log.w(TAG, "Unexpected type of credential: " + credential.type)
    }
    return null
  }

  companion object {

    private const val TAG = "AuthManager"
    private const val WEB_CLIENT_ID =
      "147122952565-fg865qq8rnofsd7699vpcjsqb2bp3sor.apps.googleusercontent.com"
  }
}

