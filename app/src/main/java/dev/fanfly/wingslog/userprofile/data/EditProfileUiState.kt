package dev.fanfly.wingslog.userprofile.data

import android.net.Uri

// This data class will hold the state of our form
data class EditProfileUiState(
  val photoUri: Uri? = null,
  val displayName: String = "",
  val licenceInfo: LicenseInfo = licenseInfo { },
  val isLoading: Boolean = false,
  val isSaved: Boolean = false,
)