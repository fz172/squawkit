package dev.fanfly.wingslog.dev.fanfly.wingslog.userprofile.data

import android.net.Uri

// This data class will hold the state of our form
data class EditProfileUiState(
  val photoUri: Uri? = null,
  val displayName: String = "",
  val licenseType: String = "Airline Transport Pilot",
  val licenseNumber: String = "",
  val expirationDate: String = "",
  val isLoading: Boolean = false,
  val isSaved: Boolean = false,
)