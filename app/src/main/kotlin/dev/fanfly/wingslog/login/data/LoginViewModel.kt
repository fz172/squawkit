package dev.fanfly.wingslog.login.data

import androidx.lifecycle.ViewModel
import dev.fanfly.wingslog.core.network.auth.AuthManager

class LoginViewModel(private val authManager: AuthManager) : ViewModel() {

  suspend fun silentLogin() = authManager.trySilentLogin()

  suspend fun login() = authManager.signInWithGoogle()
}