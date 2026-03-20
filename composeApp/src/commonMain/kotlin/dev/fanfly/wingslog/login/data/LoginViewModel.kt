package dev.fanfly.wingslog.login.data

import androidx.lifecycle.ViewModel
import dev.fanfly.wingslog.core.auth.GitLiveAuthManager

class LoginViewModel(private val authManager: GitLiveAuthManager) : ViewModel() {

  suspend fun silentLogin() = authManager.trySilentLogin()

  suspend fun login() = authManager.signInWithGoogle()
}
