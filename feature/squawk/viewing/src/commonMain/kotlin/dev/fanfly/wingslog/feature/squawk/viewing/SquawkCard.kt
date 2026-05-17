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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.sharedassets.chipColor
import dev.fanfly.wingslog.feature.squawk.sharedassets.chipTextColor
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.priority_aog
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_status_addressed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_status_dismissed

@Composable
fun SquawkCard(
  item: SquawkWithStatus,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val squawk = item.squawk
  val isAog = squawk.priority == SquawkPriority.SQUAWK_PRIORITY_AOG
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
        Row(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          PriorityBadge(item)
          StatusBadge(item.status)
        }
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

      if ((squawk.created_at?.getEpochSecond() ?: 0L) > 0L) {
        Text(
          text = squawk.created_at!!.toLocalDate().toDisplayFormat(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun StatusBadge(status: SquawkStatus) {
  val scheme = MaterialTheme.colorScheme
  val (bg, fg, label) = when (status) {
    SquawkStatus.ADDRESSED -> Triple(
      scheme.secondaryContainer,
      scheme.onSecondaryContainer,
      stringResource(Res.string.squawk_status_addressed),
    )
    SquawkStatus.DISMISSED -> Triple(
      scheme.surfaceVariant,
      scheme.onSurfaceVariant,
      stringResource(Res.string.squawk_status_dismissed),
    )
    SquawkStatus.OPEN -> return
  }
  Box(
    modifier = Modifier
      .background(bg, RoundedCornerShape(Spacing.extraSmall))
      .padding(horizontal = Spacing.small, vertical = Spacing.tiny)
  ) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )
  }
}

@Composable
internal fun PriorityBadge(item: SquawkWithStatus) {
  val priority = item.squawk.priority
  val scheme = MaterialTheme.colorScheme
  val bg = priority.chipColor(scheme)
  val fg = priority.chipTextColor(scheme)
  val label = when (priority) {
    SquawkPriority.SQUAWK_PRIORITY_AOG    -> stringResource(Res.string.priority_aog)
    SquawkPriority.SQUAWK_PRIORITY_HIGH   -> stringResource(Res.string.priority_high)
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> stringResource(Res.string.priority_medium)
    else                                   -> stringResource(Res.string.priority_low)
  }
  Box(
    modifier = Modifier
      .background(bg, RoundedCornerShape(Spacing.extraSmall))
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
