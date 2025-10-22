package dev.fanfly.wingslog.dev.fanfly.wingslog.login.data

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.dev.fanfly.wingslog.auth.AuthManager
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val authManager: AuthManager) : ViewModel() {

  suspend fun silentLogin() = authManager.trySilentLogin()

  suspend fun login() = authManager.signInWithGoogle()
}