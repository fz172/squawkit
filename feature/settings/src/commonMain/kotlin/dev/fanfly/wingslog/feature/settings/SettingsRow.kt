package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/**
 * A single clickable row for a setting item.
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit,
  settingsLevel: SettingsLevel = DEFAULT,
  subtitle: String? = null,
) {
  val tint =
    if (settingsLevel == DEFAULT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      // M3 list item minimums: ~56dp single-line, ~72dp two-line (Settings-style summary rows).
      .heightIn(min = if (subtitle != null) 72.dp else 56.dp)
      .padding(horizontal = Spacing.small, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // --- Icon ---
    Box(
      modifier = Modifier
        .size(Spacing.huge)
        .clip(RoundedCornerShape(Spacing.small))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        modifier = Modifier.size(Spacing.extraLarge),
        tint = tint
      )
    }

    Spacer(modifier = Modifier.width(Spacing.large))

    // --- Title (+ optional supporting text) ---
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = tint,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.alpha(0.6f)
        )
      }
    }
    if (settingsLevel != SettingsLevel.DANGER) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = tint
      )
    }
  }
}

@Preview
@Composable
fun SettingsRowPreview() {
  WingslogTheme {
    SettingsRow(
      icon = Icons.AutoMirrored.Filled.ArrowBack,
      title = "Test",
      subtitle = "Menu 1, item 2",
      onClick = {},
    )
  }
}
