package dev.fanfly.wingslog.feature.settings.section

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.feature.settings.NotificationsRowState
import dev.fanfly.wingslog.feature.settings.SettingsUiState
import dev.fanfly.wingslog.feature.settings.row.SettingsRow
import dev.fanfly.wingslog.feature.settings.row.SettingsRowGroup
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.settings_notifications
import wingslog.feature.settings.generated.resources.settings_notifications_subtitle_blocked
import wingslog.feature.settings.generated.resources.settings_notifications_subtitle_default
import wingslog.feature.settings.generated.resources.settings_notifications_subtitle_off
import wingslog.feature.settings.generated.resources.settings_section_preferences
import wingslog.feature.settings.generated.resources.settings_sync_subtitle
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes

/** Notifications, Backup & Sync and Appearance. */
@Composable
internal fun PreferencesSection(
  user: SettingsUiState,
  appearanceMode: AppearanceMode,
  onAppearanceChange: (AppearanceMode) -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenSync: () -> Unit,
) {
  GroupedSection(stringResource(SettingsRes.string.settings_section_preferences)) {
    SettingsRowGroup(
      listOf(
        {
          SettingsRow(
            icon = Icons.Default.Notifications,
            title = stringResource(SettingsRes.string.settings_notifications),
            subtitle = stringResource(
              when (user.notificationsRowState) {
                NotificationsRowState.BLOCKED ->
                  SettingsRes.string.settings_notifications_subtitle_blocked

                NotificationsRowState.OFF ->
                  SettingsRes.string.settings_notifications_subtitle_off

                NotificationsRowState.DEFAULT ->
                  SettingsRes.string.settings_notifications_subtitle_default
              }
            ),
            onClick = onOpenNotifications,
          )
        },
        {
          SettingsRow(
            icon = Icons.Default.CloudSync,
            title = stringResource(SyncRes.string.feature_name_backup_and_sync),
            subtitle = stringResource(SettingsRes.string.settings_sync_subtitle),
            onClick = onOpenSync,
          )
        },
        {
          AppearanceSettingRow(
            mode = appearanceMode,
            onModeChange = onAppearanceChange,
          )
        },
      )
    )
  }
}
