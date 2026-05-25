package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueStatus

@Composable
fun TaskCard(
  title: String,
  subtitle: String,
  statusLabel: String,
  statusValue: String,
  badgeText: String,
  icon: ImageVector,
  statusColor: Color,
  dueStatus: DueStatus = DueStatus.NORMAL,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val isOverdue = dueStatus == DueStatus.OVERDUE
  val isDueSoon = dueStatus == DueStatus.DUE_SOON
  val isAlert = isOverdue || isDueSoon
  val colors = MaterialTheme.statusColors
  val badgeTier = when (dueStatus) {
    DueStatus.OVERDUE -> StatusTier.CRITICAL
    DueStatus.DUE_SOON -> StatusTier.CAUTION
    DueStatus.COMPLIED -> StatusTier.POSITIVE
    DueStatus.NORMAL -> StatusTier.NEUTRAL
  }

  val borderColor = when {
    isOverdue -> colors.critical.accent.copy(alpha = 0.5f)
    isDueSoon -> statusColor.copy(alpha = 0.5f)
    else -> MaterialTheme.colorScheme.outlineVariant
  }

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      borderColor
    ),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      // Row 1: icon + status badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (badgeText.isNotBlank()) {
          StatusChip(label = badgeText, tier = badgeTier)
        } else {
          Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Spacing.large),
            tint = if (isAlert) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Row 2: title + subtitle (notes)
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Row 3: divider + label / value + chevron
      if (statusValue.isNotBlank()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
          if (statusLabel.isNotBlank()) {
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 0.8.sp,
            )
          }
          Text(
            text = statusValue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun PreviewTaskCard() = TaskCard(
  title = "100 Hr Inspection",
  subtitle = "Routine engine and airframe check",
  statusLabel = "DEADLINE",
  statusValue = "05/13/2026",
  badgeText = "OVERDUE",
  icon = Icons.Default.Schedule,
  statusColor = MaterialTheme.statusColors.critical.accent,
  dueStatus = DueStatus.OVERDUE,
  modifier = Modifier.fillMaxWidth()
)
