package dev.fanfly.wingslog.feature.userprofile.data

import android.net.Uri
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.licenseInfo

// This data class will hold the state of our form
data class EditProfileUiState(
  val photoUri: Uri? = null,
  val displayName: String = "",
  val licenceInfo: LicenseInfo = licenseInfo { },
  val isLoading: Boolean = false,
  val isSaved: Boolean = false,
)