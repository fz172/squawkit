package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags

data class SettingsUiState(
  val photoUri: String? = null,
  val selfTechnician: Technician? = null,
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: FeatureFlags = FeatureFlags(),
  // Guest (anonymous) accounts keep all data on-device only; logging out erases it permanently.
  val isAnonymous: Boolean = false,
  // Debug or dogfood build: gates developer-only entries like Feature Lab.
  val isDeveloperBuild: Boolean = false,
)

enum class UserStatus {
  UNKNOWN,
  LOADING,
  LOGGED_IN,
  LOGGED_OUT,
}
