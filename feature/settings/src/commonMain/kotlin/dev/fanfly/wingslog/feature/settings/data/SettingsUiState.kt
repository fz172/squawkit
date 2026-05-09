package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags

data class SettingsUiState(
  val photoUri: String? = null,
  val displayName: String? = null,
  val licenseInfo: LicenseInfo = newUserLicenseProfile(),
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: FeatureFlags = FeatureFlags(),
)

enum class UserStatus {
  UNKNOWN,
  LOADING,
  LOGGED_IN,
  LOGGED_OUT,
}