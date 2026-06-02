package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.settings.SettingsLevel.DEFAULT

enum class SettingsLevel {
  DEFAULT,
  DANGER
}

// The leading icon chip — 48dp square with a 13dp radius, matching the Settings design handoff.
private val IconChipSize = 48.dp
private val IconChipRadius = 13.dp
private val IconSize = 22.dp

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

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(Spacing.xLarge),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // --- Leading icon chip ---
    Box(
      modifier = Modifier
        .size(IconChipSize)
        .clip(RoundedCornerShape(IconChipRadius))
        .background(chipColor),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        modifier = Modifier.size(IconSize),
        tint = iconTint,
      )
    }

    Spacer(modifier = Modifier.width(Spacing.large))

    // --- Title (+ optional subtitle) ---
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = titleColor,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // The destructive row drops the chevron — it's an action, not a navigation entry.
    if (!danger) {
      Spacer(modifier = Modifier.width(Spacing.large))
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
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
