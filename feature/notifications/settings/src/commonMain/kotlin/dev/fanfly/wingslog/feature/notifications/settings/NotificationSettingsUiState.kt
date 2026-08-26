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
  /**
   * One-shot signal that the last write to [dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager]
   * failed — the toggle that triggered it has already reverted, since [settings] only changes once
   * [NotificationSettingsViewModel]'s `combine` re-emits from the real store. The screen shows a
   * snackbar and calls back to clear this.
   */
  val saveError: Boolean = false,
)
