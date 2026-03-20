package dev.fanfly.wingslog.feature.userprofile.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.GitLiveAuthManager
import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class EditProfileViewModel(
  private val userProfileManager: UserProfileManager,
  authManager: GitLiveAuthManager
) : ViewModel() {


  private val _uiState: MutableStateFlow<EditProfileUiState> =
    MutableStateFlow(authManager.getCurrentUser()?.toEditProfileUiState() ?: EditProfileUiState())
  val uiState = _uiState.asStateFlow()

  init {
    // Load the user's data when the ViewModel is created
    loadUserData()
  }

  private fun loadUserData() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      userProfileManager.observeLicenseInfo().collect { result ->
        if (result != null) {
          _uiState.update {
            it.copy(
              licenceInfo = result,
              isLoading = false
            )
          }
        } else {
          _uiState.update { it.copy(isLoading = false) }
        }
      }
    }
  }

  // --- Event Handlers ---

  fun onLicenseTypeChanged(newType: LicenseType) {
    _uiState.update { it.copy(licenceInfo = it.licenceInfo.copy(license_type = newType)) }
  }

  fun onLicenseNumberChanged(newNumber: String) {
    _uiState.update { it.copy(licenceInfo = it.licenceInfo.copy(license_number = newNumber)) }
  }

  fun onExpirationDateChanged(newDate: Instant) {
    _uiState.update {
      it.copy(
        licenceInfo = it.licenceInfo.copy(
          expiration_date = newDate
        )
      )
    }
  }

  fun onExpirationNeverFlagChanged(neverExpires: Boolean) {
    _uiState.update {
      it.copy(
        licenceInfo = it.licenceInfo.copy(
          expireLimit = if (neverExpires) LicenseExpireLimit.NEVER_EXPIRES else LicenseExpireLimit.EXPIRES
        )
      )
    }
  }

  fun saveChanges() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val result = userProfileManager.updateLicenseInfo(uiState.value.licenceInfo)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaved = true) }
      } else {
        // TODO: handle errors.
      }

      _uiState.update { it.copy(isLoading = false) }
    }
  }
}

fun FirebaseUser.toEditProfileUiState() =
  EditProfileUiState(
    photoUri = photoURL,
    displayName = displayName ?: "Guest"
  )
