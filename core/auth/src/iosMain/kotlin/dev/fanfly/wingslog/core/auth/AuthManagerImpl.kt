package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.AuthCredential
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

  /**
   * iOS upgrade uses Sign in with Apple. The native ASAuthorization flow (nonce + identity token →
   * OAuthProvider.credential("apple.com", …) → linkWithCredential) is not wired yet; until then this
   * reports a failure rather than silently doing nothing. Tracked in docs/account_upgrade_design.html.
   */
  override suspend fun upgradeAnonymousAccount(): AccountUpgradeResult {
    logger.w { "upgradeAnonymousAccount() not yet implemented on iOS (pending Sign in with Apple)" }
    return AccountUpgradeResult.Failed("Account upgrade is not available on iOS yet")
  }

  override suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult {
    return try {
      authProvider.signInWithCredential(credential)
      val user = authProvider.currentUser
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
    private val logger = Logger.withTag("AuthManagerImpl-iOS")
  }
}
