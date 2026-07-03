package dev.fanfly.wingslog.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.firebase_logging_subtitle
import wingslog.feature.settings.generated.resources.firebase_logging_title
import wingslog.feature.settings.generated.resources.Res as SettingsRes

/**
 * The Firebase Logging setting: toggles Firebase Analytics collection on/off, persisted
 * device-locally. Rendered as a row inside the same [SettingsCard] as [AppearanceSettingRow].
 */
@Composable
fun FirebaseLoggingSettingRow(
  enabled: Boolean,
  onEnabledChange: (Boolean) -> Unit,
) {
  val title = stringResource(SettingsRes.string.firebase_logging_title)
  GroupedRow(
    title = title,
    subtitle = stringResource(SettingsRes.string.firebase_logging_subtitle),
    onClick = { onEnabledChange(!enabled) },
    leading = {
      GroupedLeadingIconChip(
        icon = Icons.Default.Analytics,
        contentDescription = title,
      )
    },
    trailing = {
      Switch(
        checked = enabled,
        onCheckedChange = onEnabledChange,
      )
    },
  )
}
