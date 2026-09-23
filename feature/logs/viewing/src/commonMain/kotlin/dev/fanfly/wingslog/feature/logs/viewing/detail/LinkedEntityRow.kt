package dev.fanfly.wingslog.feature.logs.viewing.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun LinkedEntityRow(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: (() -> Unit)?,
) {
  Surface(
    onClick = onClick ?: {},
    enabled = onClick != null,
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = Spacing.medium,
          vertical = Spacing.medium
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(Spacing.large),
        tint = MaterialTheme.colorScheme.secondary
      )
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.padding(
          start = Spacing.extraLarge,
          end = Spacing.medium
        ),
      )
    }
  }
}
