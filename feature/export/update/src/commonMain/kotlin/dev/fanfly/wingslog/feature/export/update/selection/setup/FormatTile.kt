package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** One format as an equal-width tile: filled with a check when chosen, outlined with its icon when not. */
@Composable
internal fun FormatTile(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(Spacing.chipCornerRadius)
  val cs = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .clip(shape)
      .background(if (selected) cs.primaryContainer else Color.Transparent)
      .border(
        Spacing.hairline,
        if (selected) Color.Transparent else cs.outlineVariant,
        shape
      )
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.small, vertical = Spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(
      Spacing.small,
      Alignment.CenterHorizontally
    ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = if (selected) Icons.Default.Check else icon,
      contentDescription = null,
      modifier = Modifier.size(FormatTileIconSize),
      tint = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = if (selected) cs.onPrimaryContainer else cs.onSurface,
    )
  }
}

internal val FormatTileIconSize = 18.dp
