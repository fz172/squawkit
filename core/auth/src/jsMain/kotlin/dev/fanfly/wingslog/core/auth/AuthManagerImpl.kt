package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.await

class AuthManagerImpl(
  private val authProvider: FirebaseAuth,
) : AuthManager {

  override fun getCurrentUser(): FirebaseUser? = authProvider.currentUser

  /**
   * Tries to return the currently authenticated user without prompting.
   */
  override suspend fun trySilentLogin(): FirebaseUser? = authProvider.currentUser

  /**
   * GitLive exposes no `signInWithPopup`, so we call the modular Firebase JS Auth SDK directly
   * (see [FirebaseAuthJs]). It drives the same default-app Auth singleton GitLive wraps, so the
   * result surfaces through [FirebaseAuth.currentUser] once the popup resolves.
   */
  override suspend fun signInWithGoogle(): FirebaseUser? {
    return try {
      signInWithPopup(getAuth(), GoogleAuthProvider()).await()
      authProvider.currentUser
    } catch (e: Throwable) {
      logger.e(e) { "Google sign-in failed" }
      null
    }
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

  override suspend fun upgradeAnonymousAccount(): AccountUpgradeResult {
    logger.w { "upgradeAnonymousAccount() is not yet supported on web" }
    return AccountUpgradeResult.Failed("Account upgrade is not available on web yet")
  }

  override suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult {
    return try {
      val result = authProvider.signInWithCredential(credential)
      val user = result.user ?: authProvider.currentUser
        ?: return AccountUpgradeResult.Failed("Sign-in returned no user")
      AccountUpgradeResult.Linked(user)
    } catch (e: Exception) {
      logger.e(e) { "Sign-in to existing account failed" }
      AccountUpgradeResult.Failed(e.message ?: "Sign-in failed")
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
    private val logger = Logger.withTag("AuthManagerImpl-JS")
  }
}
