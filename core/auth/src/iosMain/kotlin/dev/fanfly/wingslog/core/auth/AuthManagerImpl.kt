package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser

class AuthManagerImpl(
  private val authProvider: FirebaseAuth,
) : AuthManager {

  override fun getCurrentUser(): FirebaseUser? = authProvider.currentUser

  /**
   * Tries to return the currently authenticated user without prompting.
   */
  override suspend fun trySilentLogin(): FirebaseUser? = authProvider.currentUser

  /**
   * Google Sign-In is not supported on iOS.
   * Returns null.
   */
  override suspend fun signInWithGoogle(): FirebaseUser? {
    logger.w { "signInWithGoogle() is not supported on iOS" }
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

  override suspend fun logOut() {
    try {
      authProvider.signOut()
    } catch (e: Exception) {
      logger.e(e) { "Error logging out" }
    }
  }

  companion object {
    private val logger = Logger.withTag("AuthManagerImpl-iOS")
  }
}
