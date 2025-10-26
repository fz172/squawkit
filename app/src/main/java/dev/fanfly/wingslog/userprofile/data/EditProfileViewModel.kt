package dev.fanfly.wingslog.userprofile.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.google.protobuf.timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.dev.fanfly.wingslog.auth.AuthManager
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(private val authManager: AuthManager) : ViewModel() {

  // Private MutableStateFlow to hold the UI state
  private val _uiState: MutableStateFlow<EditProfileUiState> =
    MutableStateFlow(authManager.getCurrentUser()?.toEditProfileUiState() ?: EditProfileUiState())

  // Public immutable StateFlow for the UI to observe
  val uiState = _uiState.asStateFlow()

  init {
    // Load the user's data when the ViewModel is created
    loadUserData()
  }

  private fun loadUserData() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      // --- TODO: Load data from Firebase/Firestore ---
      // val userData = repository.getUserProfile()
      _uiState.update {
        it.copy(
          licenceInfo = licenseInfo {
            licenseNumber = "123456789"
            expirationDate = timestamp { seconds = Instant.now().epochSecond }
            licenseType = LicenseType.REPAIRMAN
          },
          isLoading = false
        )
      }
    }
  }

  // --- Event Handlers ---

  fun onLicenseTypeChanged(newType: LicenseType) {
    _uiState.update { it.copy(licenceInfo = it.licenceInfo.copy { licenseType = newType }) }
  }

  fun onLicenseNumberChanged(newNumber: String) {
    _uiState.update { it.copy(licenceInfo = it.licenceInfo.copy { licenseNumber = newNumber }) }
  }

  fun onExpirationDateChanged(newDate: Instant) {
    _uiState.update {
      it.copy(licenceInfo = it.licenceInfo.copy {
        expirationDate = timestamp {
          seconds = newDate.epochSecond
          nanos = newDate.nano
        }
      })
    }
  }

  fun onExpirationNeverFlagChanged(neverExpires: Boolean) {
    _uiState.update {
      it.copy(licenceInfo = it.licenceInfo.copy {
        expireLimit =
          if (neverExpires) LicenseExpireLimit.NEVER_EXPIRES else LicenseExpireLimit.EXPIRES
      })
    }
  }

  fun saveChanges() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      // --- TODO: Save data to Firebase/Firestore ---
      // val success = repository.saveUserProfile(uiState.value)
      // if (success) {
      //     _uiState.update { it.copy(isSaved = true) }
      // } else {
      //     // Handle error
      // }
      _uiState.update { it.copy(isLoading = false) }
    }
  }
}

fun FirebaseUser.toEditProfileUiState() =
  EditProfileUiState(
    photoUri = photoUrl,
    displayName = displayName ?: "Guest"
  )
