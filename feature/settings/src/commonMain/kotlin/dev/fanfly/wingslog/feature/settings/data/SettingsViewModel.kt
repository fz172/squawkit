package dev.fanfly.wingslog.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
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
  private val dbChecker: DatabaseIntegrityChecker,
  private val featureLabManager: FeatureLabManager,
) : ViewModel() {

  private val _user = MutableStateFlow(SettingsUiState())
  val user: StateFlow<SettingsUiState> = _user.asStateFlow()

  private var observeLicenseJob: Job? = null

  init {
    loadUserProfile()
    observeFeatureFlags()
  }

  private fun observeFeatureFlags() {
    viewModelScope.launch {
      featureLabManager.observe().collect { flags ->
        _user.value = _user.value.copy(featureFlags = flags)
      }
    }
  }

  private fun loadUserProfile() {
    _user.value = SettingsUiState(userStatus = UserStatus.LOADING)
    observeLicenseJob?.cancel()
    observeLicenseJob = viewModelScope.launch {
      userProfileManager.observeLicenseInfo().collect { result ->
        if (result != null) {
          _user.value = _user.value.copy(
            photoUri = authManager.getCurrentUser()?.photoURL,
            displayName = authManager.getCurrentUser()?.displayName,
            licenseInfo = result,
            userStatus = UserStatus.LOGGED_IN,
          )
        }
      }
    }
  }

  fun logOut() {
    val uid = authManager.getCurrentUser()?.uid
    viewModelScope.launch {
      observeLicenseJob?.cancel()
      if (uid != null) {
        attachmentManager.wipeLocalData(uid)
        dbChecker.wipeDataForUser(uid)
      }
      authManager.logOut()
      _user.value =
        SettingsUiState(displayName = null, photoUri = null, userStatus = UserStatus.LOGGED_OUT)
    }
  }
}
