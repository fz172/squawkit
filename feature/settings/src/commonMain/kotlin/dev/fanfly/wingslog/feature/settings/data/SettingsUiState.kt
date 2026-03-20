package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile
import dev.gitlive.firebase.auth.FirebaseUser

data class SettingsUiState(
  val firebaseUser: FirebaseUser? = null,
  val licenseInfo: LicenseInfo = newUserLicenseProfile(),
  val isLoading: Boolean = true
)
