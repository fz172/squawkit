package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags

data class SettingsUiState(
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: DeveloperFlags = DeveloperFlags(),
  // Guest (anonymous) accounts keep all data on-device only; logging out erases it permanently.
  val isAnonymous: Boolean = false,
  val isDeveloperOptionsSupported: Boolean = false,
  val isSubscriptionSupported: Boolean = false,
)

enum class UserStatus {
  UNKNOWN,
  LOADING,
  LOGGED_OUT,
}
