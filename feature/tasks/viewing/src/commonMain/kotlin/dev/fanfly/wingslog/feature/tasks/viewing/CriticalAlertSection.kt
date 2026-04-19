package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.tasks.sharedassets.generated.resources.maintenance_due_title
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes
import wingslog.feature.tasks.viewing.generated.resources.critical_airworthiness
import wingslog.feature.tasks.viewing.generated.resources.due_date
import wingslog.feature.tasks.viewing.generated.resources.label_due_engine
import wingslog.feature.tasks.viewing.generated.resources.label_expired
import wingslog.feature.tasks.viewing.generated.resources.maintenance_due_subtitle


@Composable
fun CriticalAlertsSection(
  overdueInspections: List<MaintenanceTaskWithStatus>,
  onCardClick: (MaintenanceTaskWithStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasOverdue = overdueInspections.any { it.dueStatus.status == DueStatus.OVERDUE }
  val titleColor = if (hasOverdue) MaterialTheme.colorScheme.error else StatusWarning

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column {
      // --- Card header: title + subtitle ---
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Spacing.large, vertical = Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Text(
          text = stringResource(SharedInspectionRes.string.maintenance_due_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = titleColor,
        )
        Text(
          text = stringResource(ViewingRes.string.maintenance_due_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

      // --- Attention list ---
      Column(
        modifier = Modifier.padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Text(
          text = stringResource(ViewingRes.string.critical_airworthiness),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          letterSpacing = 1.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          overdueInspections.forEach { inspection ->
            CriticalAlertItem(
              cardWithStatus = inspection,
              onClick = { onCardClick(inspection) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CriticalAlertItem(
  cardWithStatus: MaintenanceTaskWithStatus,
  onClick: () -> Unit,
) {
  val isOverdue = cardWithStatus.dueStatus.status == DueStatus.OVERDUE
  val dotColor = if (isOverdue) MaterialTheme.colorScheme.error else StatusWarning

  val dueDate = cardWithStatus.dueStatus.nextDueDate
  val dueEngine = cardWithStatus.dueStatus.nextDueEngine
  val statusText = when {
    isOverdue && dueDate != null ->
      stringResource(ViewingRes.string.label_expired, dueDate.toDisplayFormat())

    isOverdue && dueEngine != null ->
      stringResource(ViewingRes.string.label_expired, "${dueEngine} HRS")

    dueDate != null ->
      stringResource(ViewingRes.string.due_date, dueDate.toDisplayFormat())

    dueEngine != null ->
      stringResource(ViewingRes.string.label_due_engine) + " ${dueEngine} HRS"

    else -> ""
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.tiny),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier
        .padding(top = 5.dp)
        .size(6.dp)
        .background(dotColor, CircleShape)
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
      Text(
        text = cardWithStatus.card.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (statusText.isNotBlank()) {
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelSmall,
          color = dotColor,
        )
      }
    }
  }
}
