package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** Dialog card: outlined tile in a grid cell, no chevron. */
@Composable
internal fun OptionCard(option: PickerOption, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(Spacing.cardCornerRadius)
  Row(
    modifier = modifier
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
      .clickable(enabled = option.enabled, onClick = option.onClick)
      .padding(Spacing.medium)
      .alpha(if (option.enabled) 1f else DISABLED_ALPHA),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    OptionContent(option)
  }
}
