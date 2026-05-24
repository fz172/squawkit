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
