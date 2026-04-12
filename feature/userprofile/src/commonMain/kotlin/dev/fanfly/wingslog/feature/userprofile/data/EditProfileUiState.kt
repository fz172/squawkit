package dev.fanfly.wingslog.feature.userprofile.data

import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile

// This data class will hold the state of our form
data class EditProfileUiState(
  val photoUri: String? = null,
  val displayName: String? = null,
  val licenceInfo: LicenseInfo = newUserLicenseProfile(),
  val initialLicenceInfo: LicenseInfo? = null,
  val isLoading: Boolean = false,
  val isSaved: Boolean = false,
) {
  val hasChanges: Boolean
    get() = initialLicenceInfo != null && licenceInfo != initialLicenceInfo
}