package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors

@Composable
fun SquawkRailRow(
  title: String,
  subtitle: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      tint = MaterialTheme.statusColors.critical.accent,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
