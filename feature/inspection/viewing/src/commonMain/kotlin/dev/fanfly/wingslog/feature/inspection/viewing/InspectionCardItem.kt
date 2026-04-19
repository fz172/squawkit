package dev.fanfly.wingslog.feature.inspection.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.inspection.model.DueMetadata
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.dash
import wingslog.feature.inspection.viewing.generated.resources.badge_due
import wingslog.feature.inspection.viewing.generated.resources.badge_overdue
import wingslog.feature.inspection.viewing.generated.resources.complied
import wingslog.feature.inspection.viewing.generated.resources.label_deadline
import wingslog.feature.inspection.viewing.generated.resources.label_due_engine
import wingslog.feature.inspection.viewing.generated.resources.on_condition
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.inspection.viewing.generated.resources.Res as ViewingRes

@Composable
fun InspectionCardItem(
  cardWithStatus: InspectionCardWithStatus,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val status = cardWithStatus.dueStatus.status
  val dueDate = cardWithStatus.dueStatus.nextDueDate
  val dueEngine = cardWithStatus.dueStatus.nextDueEngine
  val isOnCondition = cardWithStatus.dueStatus.isOnCondition

  val statusColor = when (status) {
    DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
    DueStatus.DUE_SOON -> StatusWarning
    DueStatus.COMPLIED -> StatusOk
    DueStatus.NORMAL -> if (isOnCondition) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.primary
  }

  val badgeText = when (status) {
    DueStatus.OVERDUE -> stringResource(ViewingRes.string.badge_overdue)
    DueStatus.DUE_SOON -> stringResource(ViewingRes.string.badge_due)
    else -> ""
  }

  val icon = when {
    status == DueStatus.OVERDUE || status == DueStatus.DUE_SOON -> Icons.Filled.Warning
    dueEngine != null -> Icons.Filled.Build
    status == DueStatus.COMPLIED -> Icons.Default.CheckCircle
    else -> Icons.Default.CalendarToday
  }

  val statusLabel = when {
    status == DueStatus.COMPLIED || isOnCondition -> ""
    dueDate != null -> stringResource(ViewingRes.string.label_deadline)
    dueEngine != null -> stringResource(ViewingRes.string.label_due_engine)
    else -> ""
  }

  val statusValue = when {
    status == DueStatus.COMPLIED -> stringResource(ViewingRes.string.complied)
    isOnCondition -> stringResource(ViewingRes.string.on_condition)
    dueDate != null -> dueDate.toDisplayFormat()
    dueEngine != null -> "${dueEngine.toDouble().formatToOneDecimalPlace()} HRS"
    else -> stringResource(CoreRes.string.dash)
  }

  InspectionCard(
    title = cardWithStatus.card.title,
    subtitle = cardWithStatus.card.notes,
    statusLabel = statusLabel,
    statusValue = statusValue,
    badgeText = badgeText,
    icon = icon,
    statusColor = statusColor,
    dueStatus = status,
    onClick = onClick,
    modifier = modifier,
  )
}

private fun previewCard(title: String, notes: String = "") =
  InspectionCard(title = title, notes = notes)

@Preview
@Composable
fun PreviewInspectionCardItemOverdue() = InspectionCardItem(
  cardWithStatus = InspectionCardWithStatus(
    card = previewCard("100 Hr Inspection", "Routine engine and airframe check"),
    dueStatus = DueMetadata(
      nextDueDate = LocalDate(2025, 12, 1),
      status = DueStatus.OVERDUE,
    ),
  ),
)

@Preview
@Composable
fun PreviewInspectionCardItemDueSoon() = InspectionCardItem(
  cardWithStatus = InspectionCardWithStatus(
    card = previewCard("Annual Inspection"),
    dueStatus = DueMetadata(
      nextDueEngine = 1250f,
      status = DueStatus.DUE_SOON,
    ),
  ),
)

@Preview
@Composable
fun PreviewInspectionCardItemComplied() = InspectionCardItem(
  cardWithStatus = InspectionCardWithStatus(
    card = previewCard("Oil Change", "Every 50 hours"),
    dueStatus = DueMetadata(status = DueStatus.COMPLIED),
  ),
)

@Preview
@Composable
fun PreviewInspectionCardItemOnCondition() = InspectionCardItem(
  cardWithStatus = InspectionCardWithStatus(
    card = previewCard("Prop Strike Inspection"),
    dueStatus = DueMetadata(isOnCondition = true, status = DueStatus.NORMAL),
  ),
)
