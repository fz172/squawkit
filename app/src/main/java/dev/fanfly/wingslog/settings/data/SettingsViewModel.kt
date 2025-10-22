package dev.fanfly.wingslog.dev.fanfly.wingslog.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.login.data.AuthManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val authManager: AuthManager) : ViewModel() {

  private val _user = MutableStateFlow(authManager.getCurrentUser())
  val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

  /**
   * Logs the user out of Firebase and clears any saved credentials
   * from Credential Manager.
   */
  fun logOut() {
    // Clear credentials from Credential Manager
    viewModelScope.launch {
      // Sign out of Firebase
      authManager.logOut()
      // Update the UI state to reflect no user is logged in
      _user.value = null
    }
  }
}