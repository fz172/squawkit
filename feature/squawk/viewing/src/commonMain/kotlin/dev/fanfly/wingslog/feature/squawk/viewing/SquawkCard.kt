package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.sharedassets.chipColor
import dev.fanfly.wingslog.feature.squawk.sharedassets.chipTextColor
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.addressed_by
import wingslog.feature.squawk.sharedassets.generated.resources.priority_aog
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium

@Composable
fun SquawkCard(
  item: SquawkWithStatus,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val squawk = item.squawk
  val isAog = squawk.priority == dev.fanfly.wingslog.aircraft.SquawkPriority.SQUAWK_PRIORITY_AOG
  val borderColor = if (isAog)
    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
  else
    MaterialTheme.colorScheme.outlineVariant

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, borderColor),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PriorityChip(item)
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
          text = squawk.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (squawk.description.isNotBlank()) {
          Text(
            text = squawk.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (item.status == SquawkStatus.ADDRESSED) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Text(
          text = stringResource(Res.string.addressed_by),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 0.8.sp,
        )
      }
    }
  }
}

@Composable
private fun PriorityChip(item: SquawkWithStatus) {
  val priority = item.squawk.priority
  val scheme = MaterialTheme.colorScheme
  val bg = priority.chipColor(scheme)
  val fg = priority.chipTextColor(scheme)
  val label = when (priority) {
    dev.fanfly.wingslog.aircraft.SquawkPriority.SQUAWK_PRIORITY_AOG    -> stringResource(Res.string.priority_aog)
    dev.fanfly.wingslog.aircraft.SquawkPriority.SQUAWK_PRIORITY_HIGH   -> stringResource(Res.string.priority_high)
    dev.fanfly.wingslog.aircraft.SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> stringResource(Res.string.priority_medium)
    else                                                                 -> stringResource(Res.string.priority_low)
  }
  Box(
    modifier = Modifier
      .background(bg.copy(alpha = 0.15f), RoundedCornerShape(Spacing.extraSmall))
      .padding(horizontal = Spacing.small, vertical = Spacing.tiny)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )
  }
}
