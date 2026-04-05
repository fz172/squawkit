package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile

data class SettingsUiState(
  val photoUri: String? = null,
  val displayName: String? = null,
  val licenseInfo: LicenseInfo = newUserLicenseProfile(),
  val userStatus: UserStatus = UserStatus.UNKNOWN,
)

enum class UserStatus {
  UNKNOWN,
  LOADING,
  LOGGED_IN,
  LOGGED_OUT,
}