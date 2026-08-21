package dev.fanfly.wingslog.feature.notifications.settings

import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState

/** Pure render input for [NotificationSettingsScreen]. Design §9.2. */
data class NotificationSettingsUiState(
  val settings: NotificationSettings = NotificationSettings(),
  val permission: PermissionState = PermissionState.UNDETERMINED,
  val canOpenSystemSettings: Boolean = false,
  /** Real account, not anonymous. */
  val isSignedIn: Boolean = false,
  val isCloudSyncEnabled: Boolean = false,
  val isLoading: Boolean = true,
  /** Q5 — mutable-with-confirmation is the whole point, so this is not just a display flag. */
  val confirmDisableAog: Boolean = false,
)
