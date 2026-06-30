package dev.fanfly.wingslog.core.auth

import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseUser

interface AuthManager {
  fun getCurrentUser(): FirebaseUser?
  suspend fun trySilentLogin(): FirebaseUser?
  suspend fun signInWithGoogle(): FirebaseUser?
  suspend fun signInAnonymously(): FirebaseUser?
  suspend fun logOut()

  /**
   * Passwordless email-link sign-in, leg 1: emails a one-time sign-in link to [email]. See
   * docs/account/email_link_signin_design.html.
   */
  suspend fun sendSignInLink(email: String): SendLinkResult

  /** True when [link] is a Firebase email sign-in link, so deep-link hosts can ignore other URLs. */
  fun isSignInWithEmailLink(link: String): Boolean

  /**
   * Passwordless email-link sign-in, leg 2: completes auth for the [email] the [link] was issued
   * for. Returns null when [link] is not a sign-in link or completion fails.
   */
  suspend fun completeSignInLink(email: String, link: String): FirebaseUser?

  /**
   * Links the platform's primary provider (Google on Android, Apple on iOS) to the current
   * anonymous user, preserving the UID so local-first data stays valid. Returns
   * [AccountUpgradeResult.CredentialInUse] when the chosen account already exists (caller then
   * offers the merge path).
   */
  suspend fun upgradeAnonymousAccount(): AccountUpgradeResult

  /**
   * Merge path: signs in to the existing account that owns [credential] (a different UID). The
   * caller is responsible for re-keying local data to the new UID afterward.
   */
  suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult
}
