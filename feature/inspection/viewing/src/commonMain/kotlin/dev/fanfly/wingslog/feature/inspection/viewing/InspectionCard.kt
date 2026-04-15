package dev.fanfly.wingslog.feature.inspection.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.inspection.model.DueStatus

@Composable
fun InspectionCard(
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

  val borderColor = when {
    isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    isDueSoon -> statusColor.copy(alpha = 0.5f)
    else -> MaterialTheme.colorScheme.surfaceVariant
  }

  Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, borderColor),
    shape = RoundedCornerShape(Spacing.chipCornerRadius),
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
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = if (isAlert) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (badgeText.isNotBlank()) {
          StatusBadge(text = badgeText, color = if (isOverdue) MaterialTheme.colorScheme.error else statusColor)
        }
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
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
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
  Box(
    modifier = Modifier
      .background(color.copy(alpha = 0.12f), RoundedCornerShape(Spacing.extraSmall))
      .padding(horizontal = Spacing.small, vertical = Spacing.tiny)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )
  }
}

@Preview
@Composable
fun PreviewInspectionCard() = InspectionCard(
  title = "100 Hr Inspection",
  subtitle = "Routine engine and airframe check",
  statusLabel = "DEADLINE",
  statusValue = "05/13/2026",
  badgeText = "OVERDUE",
  icon = Icons.Default.Schedule,
  statusColor = MaterialTheme.colorScheme.error,
  dueStatus = DueStatus.OVERDUE,
  modifier = Modifier.fillMaxWidth()
)
