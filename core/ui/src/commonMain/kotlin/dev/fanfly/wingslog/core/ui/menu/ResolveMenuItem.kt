package dev.fanfly.wingslog.core.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun ResolveMenuItem(action: ResolveMenuAction) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(ItemCornerRadius))
      .clickable(onClick = action.onClick)
      .padding(
        horizontal = Spacing.medium,
        vertical = if (action.subtitle == null) ItemPaddingSingleLine
        else ItemPaddingTwoLine,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(ItemIconSize)
        .background(
          action.iconBackground,
          RoundedCornerShape(ItemIconCornerRadius)
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = action.icon,
        contentDescription = null,
        tint = action.iconTint,
        modifier = Modifier.size(ItemIconGlyphSize),
      )
    }
    Spacer(modifier = Modifier.width(Spacing.medium))
    Column {
      Text(
        text = action.label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      action.subtitle?.let { subtitle ->
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
