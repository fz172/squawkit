package dev.fanfly.wingslog.settings.data

import com.google.firebase.auth.FirebaseUser
import dev.fanfly.wingslog.userprofile.data.LicenseInfo
import dev.fanfly.wingslog.userprofile.data.newUserLicenseProfile

data class SettingsUiState(
  val firebaseUser: FirebaseUser? = null,
  val licenseInfo: LicenseInfo = newUserLicenseProfile(),
  val isLoading: Boolean = true
)
