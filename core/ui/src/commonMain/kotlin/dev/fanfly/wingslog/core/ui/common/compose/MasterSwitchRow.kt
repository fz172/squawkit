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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * The prominent, tinted control for the one switch every other toggle on a settings screen is
 * subordinate to — Cloud Sync on Backup & Sync, All notifications on Notifications. Primary
 * container tint when checked, not a status color: the visual weight signals "this one governs the
 * others," never a compliance or caution reading. Dims to a plain neutral card when off rather than
 * losing the tint outright, so "everything below is off" still reads as a settings row, not a
 * warning. Pairs with [SwitchRowCard] for the subordinate toggles below it — same row shape, plain
 * surface, so the contrast between the two does the hierarchy work.
 */
@Composable
fun MasterSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val containerColor =
    if (checked) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
  val onContainerColor =
    if (checked) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
  val contentAlpha = if (enabled) 1f else 0.42f

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(containerColor)
      .padding(Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = onContainerColor.copy(alpha = contentAlpha),
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = onContainerColor.copy(alpha = contentAlpha * 0.85f),
      )
    }
    Spacer(Modifier.width(Spacing.large))
    Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
  }
}
