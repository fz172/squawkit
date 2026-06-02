package dev.fanfly.wingslog.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.core.ui.common.compose.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRow
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.settings.SettingsLevel.DEFAULT

enum class SettingsLevel {
  DEFAULT,
  DANGER
}

/**
 * A single clickable navigation row inside a [SettingsCard]: a tinted icon chip, a title with an
 * optional subtitle, and (for non-destructive rows) a trailing chevron. Rows fill the card width;
 * the surrounding [SettingsCard] supplies the card surface and inter-row dividers.
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit,
  settingsLevel: SettingsLevel = DEFAULT,
  subtitle: String? = null,
) {
  val danger = settingsLevel == SettingsLevel.DANGER
  val titleColor =
    if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  val chipColor =
    if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
  val iconTint =
    if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

  GroupedRow(
    title = title,
    subtitle = subtitle,
    titleColor = titleColor,
    onClick = onClick,
    leading = {
      GroupedLeadingIconChip(
        icon = icon,
        contentDescription = title,
        containerColor = chipColor,
        iconTint = iconTint,
      )
    },
    trailing = if (danger) null else ({
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }),
  )
}

@Preview
@Composable
fun SettingsRowPreview() {
  WingslogTheme {
    SettingsCard {
      SettingsRow(
        icon = Icons.AutoMirrored.Filled.ArrowForward,
        title = "Export logs",
        subtitle = "Export and share your logbook",
        onClick = {},
      )
    }
  }
}
