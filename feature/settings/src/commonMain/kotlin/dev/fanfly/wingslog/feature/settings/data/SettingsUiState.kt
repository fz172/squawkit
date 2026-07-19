package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags

data class SettingsUiState(
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: FeatureFlags = FeatureFlags(),
  // Guest (anonymous) accounts keep all data on-device only; logging out erases it permanently.
  val isAnonymous: Boolean = false,
  val isFeatureLabSupported: Boolean = false,
)

enum class UserStatus {
  UNKNOWN,
  LOADING,
  LOGGED_OUT,
}
