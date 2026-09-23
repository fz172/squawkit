package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * A primary-tinted "do something" row at the foot of a group — "Add certification". The icon sits
 * where a chip would, so the title lines up with the rows above it.
 */
@Composable
fun GroupedActionRow(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val tint =
    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.42f)
  GroupedRow(
    title = title,
    titleColor = tint,
    enabled = enabled,
    onClick = onClick,
    leading = {
      Box(
        modifier = Modifier.size(IconChipSize),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(Spacing.extraLarge),
          tint = tint,
        )
      }
    },
    modifier = modifier,
  )
}
