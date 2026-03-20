package dev.fanfly.wingslog.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.GitLiveAuthManager
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val authManager: GitLiveAuthManager, private val userProfileManager: UserProfileManager
) : ViewModel() {

  private val _user = MutableStateFlow(SettingsUiState())
  val user: StateFlow<SettingsUiState> = _user.asStateFlow()

  init {
    viewModelScope.launch {
      userProfileManager.observeLicenseInfo().collect { result ->
        if (result != null) {
          _user.value = SettingsUiState(
            firebaseUser = authManager.getCurrentUser(), licenseInfo = result, isLoading = false
          )
        }
      }
    }
  }

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
      _user.value = SettingsUiState()
    }
  }
}
