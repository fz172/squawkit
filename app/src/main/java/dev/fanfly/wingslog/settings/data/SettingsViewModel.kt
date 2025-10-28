package dev.fanfly.wingslog.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.auth.AuthManager
import dev.fanfly.wingslog.userprofile.manager.UserProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val authManager: AuthManager,
  private val userProfileManager: UserProfileManager
) : ViewModel() {

  private val _user = MutableStateFlow(SettingsUiState())
  val user: StateFlow<SettingsUiState> = _user.asStateFlow()

  init {
    viewModelScope.launch {
      val result = userProfileManager.loadLicenseInfo()
      if (result.isSuccess) {
        _user.value = SettingsUiState(
          firebaseUser = authManager.getCurrentUser(),
          licenseInfo = result.getOrThrow(),
          isLoading = false
        )
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