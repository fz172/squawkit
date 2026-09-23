package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun GroupedLeadingIconChip(
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Box(
    modifier = modifier
      .size(IconChipSize)
      .clip(RoundedCornerShape(IconChipRadius))
      .background(containerColor),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(IconSize),
      tint = iconTint,
    )
  }
}
