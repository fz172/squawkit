package dev.fanfly.wingslog.core.auth

import dev.gitlive.firebase.auth.FirebaseUser

interface AuthManager {
  fun getCurrentUser(): FirebaseUser?
  suspend fun trySilentLogin(): FirebaseUser?
  suspend fun signInWithGoogle(): FirebaseUser?
  suspend fun logOut()
}
