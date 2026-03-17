package dev.fanfly.wingslog.feature.settings.data

import com.google.firebase.auth.FirebaseUser
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile

data class SettingsUiState(
  val firebaseUser: FirebaseUser? = null,
  val licenseInfo: LicenseInfo = newUserLicenseProfile(),
  val isLoading: Boolean = true
)
