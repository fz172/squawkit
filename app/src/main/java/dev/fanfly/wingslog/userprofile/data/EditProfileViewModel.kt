package dev.fanfly.wingslog.dev.fanfly.wingslog.userprofile.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.dev.fanfly.wingslog.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
          // licenseType = userData.licenseType,
          licenseNumber = "123456789", // Placeholder
          expirationDate = "12/31/2025", // Placeholder
          isLoading = false
        )
      }
    }
  }

  // --- Event Handlers ---

  fun onLicenseTypeChanged(newType: LicenseType) {
    _uiState.update { it.copy(licenseType = newType) }
  }

  fun onLicenseNumberChanged(newNumber: String) {
    _uiState.update { it.copy(licenseNumber = newNumber) }
  }

  fun onExpirationDateChanged(newDate: String) {
    _uiState.update { it.copy(expirationDate = newDate) }
  }

  fun onExpirationNeverFlagChanged(neverExpires: Boolean) {
    _uiState.update { it.copy(licenseNeverExpires = neverExpires) }
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
