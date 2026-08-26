package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** One row of [SwitchRowCard]. */
data class SwitchRowItem(
  val title: String,
  val subtitle: String,
  val checked: Boolean,
  val enabled: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
)

/**
 * Card-backed group of on/off [Switch] rows — the settings-screen toggle-list pattern shared by
 * Sync and Notifications (dimmed title/subtitle when a row is disabled, a divider between rows,
 * never between the card edge and its first/last row).
 */
@Composable
fun SwitchRowCard(
  items: List<SwitchRowItem>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    items.forEachIndexed { index, item ->
      SwitchRow(item)
      if (index < items.lastIndex) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
      }
    }
  }
}

@Composable
private fun SwitchRow(item: SwitchRowItem) {
  val titleColor =
    if (item.enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
  val subtitleColor =
    if (item.enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.large, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(text = item.title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
      Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
    }
    Spacer(Modifier.width(Spacing.large))
    Switch(checked = item.checked, enabled = item.enabled, onCheckedChange = item.onCheckedChange)
  }
}
