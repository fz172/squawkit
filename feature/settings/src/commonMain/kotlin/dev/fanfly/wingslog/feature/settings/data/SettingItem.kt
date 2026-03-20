package dev.fanfly.wingslog.feature.settings.data

import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.feature.settings.SettingsLevel

/**
 * A data class to hold information for a setting item.
 */
data class SettingItem(
  val icon: ImageVector,
  val title: String,
  val onClick: () -> Unit = {},
  val settingsLevel: SettingsLevel = SettingsLevel.DEFAULT,
)