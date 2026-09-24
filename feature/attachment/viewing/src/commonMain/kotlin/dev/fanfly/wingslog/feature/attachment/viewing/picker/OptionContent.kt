package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun RowScope.OptionContent(option: PickerOption) {
  val tileShape = RoundedCornerShape(Spacing.smallCornerRadius)
  Box(
    modifier = Modifier
      .clip(tileShape)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.secondaryContainer,
        tileShape
      )
      .padding(Spacing.small),
  ) {
    Icon(
      option.icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(Spacing.extraLarge),
    )
  }
  Column(modifier = Modifier.weight(1f)) {
    Text(
      text = option.title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = option.description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
