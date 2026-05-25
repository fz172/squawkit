package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.first

class AuthManagerImpl(
  private val authProvider: FirebaseAuth,
) : AuthManager {

  override fun getCurrentUser(): FirebaseUser? = authProvider.currentUser

  /**
   * Tries to return the currently authenticated user without prompting.
   *
   * Firebase JS restores the persisted session (including anonymous) from IndexedDB
   * asynchronously, so `currentUser` is still null at page load. Await the first
   * `authStateChanged` emission instead — that fires once the SDK has resolved the
   * persisted state, so a returning user is recognized across reloads.
   */
  override suspend fun trySilentLogin(): FirebaseUser? = authProvider.authStateChanged.first()

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
   * Anonymous (guest) sign-in is not supported on web — web requires a real account. The login
   * screen hides the anonymous option here (see `feature/login` `isAnonymousLoginSupported`); this
   * guards the path in case it is ever invoked.
   */
  override suspend fun signInAnonymously(): FirebaseUser? {
    logger.w { "Anonymous sign-in is not supported on web" }
    return null
  }

  /**
   * No-op on web: anonymous users don't exist here, so there is nothing to upgrade.
   */
  override suspend fun upgradeAnonymousAccount(): AccountUpgradeResult {
    logger.w { "upgradeAnonymousAccount() is not applicable on web (no anonymous users)" }
    return AccountUpgradeResult.Failed("Anonymous accounts are not supported on web")
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
