package dev.fanfly.wingslog.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val authManager: AuthManager,
  private val userProfileManager: UserProfileManager,
  private val attachmentManager: AttachmentManager,
) : ViewModel() {

  private val _user = MutableStateFlow(SettingsUiState())
  val user: StateFlow<SettingsUiState> = _user.asStateFlow()

  private var observeLicenseJob: Job? = null

  init {
    loadUserProfile()
  }

  private fun loadUserProfile() {
    _user.value = SettingsUiState(userStatus = UserStatus.LOADING)
    observeLicenseJob?.cancel()
    observeLicenseJob = viewModelScope.launch {
      userProfileManager.observeLicenseInfo().collect { result ->
        if (result != null) {
          _user.value = SettingsUiState(
            photoUri = authManager.getCurrentUser()?.photoURL,
            displayName = authManager.getCurrentUser()?.displayName,
            licenseInfo = result,
            userStatus = UserStatus.LOGGED_IN
          )
        }
      }
    }
  }

  fun wipeLocalAttachments() {
    val uid = authManager.getCurrentUser()?.uid ?: return
    viewModelScope.launch { attachmentManager.wipeLocalData(uid) }
  }

  /**
   * Logs the user out of Firebase and clears any saved credentials
   * from Credential Manager.
   */
  fun logOut() {
    // Clear credentials from Credential Manager
    viewModelScope.launch {
      // Cancel observation first to avoid permission errors
      observeLicenseJob?.cancel()
      // Sign out of Firebase
      authManager.logOut()
      // Update the UI state to reflect no user is logged in
      _user.value =
        SettingsUiState(displayName = null, photoUri = null, userStatus = UserStatus.LOGGED_OUT)
    }
  }
}
