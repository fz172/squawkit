package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** Phone row: icon tile, title + description, chevron. */
@Composable
internal fun OptionRow(option: PickerOption) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .clickable(enabled = option.enabled, onClick = option.onClick)
      .padding(Spacing.small)
      .alpha(if (option.enabled) 1f else DISABLED_ALPHA),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    OptionContent(option)
    Icon(
      Icons.Default.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(Spacing.xLarge),
    )
  }
}
