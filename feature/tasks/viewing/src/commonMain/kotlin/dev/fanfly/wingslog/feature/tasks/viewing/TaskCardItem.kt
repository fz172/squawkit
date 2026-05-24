package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.dash
import wingslog.feature.tasks.viewing.generated.resources.badge_due
import wingslog.feature.tasks.viewing.generated.resources.badge_overdue
import wingslog.feature.tasks.viewing.generated.resources.completed
import wingslog.feature.tasks.viewing.generated.resources.engine_hours_upper
import wingslog.feature.tasks.viewing.generated.resources.label_deadline
import wingslog.feature.tasks.viewing.generated.resources.label_due_engine
import wingslog.feature.tasks.viewing.generated.resources.on_condition
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes

@Composable
fun TaskCardItem(
  cardWithStatus: MaintenanceTaskWithStatus,
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
    else StatusOk
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
    status == DueStatus.COMPLIED -> stringResource(ViewingRes.string.completed)
    isOnCondition -> stringResource(ViewingRes.string.on_condition)
    dueDate != null -> dueDate.toDisplayFormat()
    dueEngine != null -> stringResource(
      ViewingRes.string.engine_hours_upper,
      dueEngine.toDouble()
        .formatToOneDecimalPlace(),
    )

    else -> stringResource(CoreRes.string.dash)
  }

  TaskCard(
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
  MaintenanceTask(title = title, notes = notes)

@Preview
@Composable
fun PreviewTaskCardItemOverdue() = TaskCardItem(
  cardWithStatus = MaintenanceTaskWithStatus(
    card = previewCard(
      "100 Hr Inspection",
      "Routine engine and airframe check"
    ),
    dueStatus = DueMetadata(
      nextDueDate = LocalDate(2025, 12, 1),
      status = DueStatus.OVERDUE,
    ),
  ),
)

@Preview
@Composable
fun PreviewTaskCardItemDueSoon() = TaskCardItem(
  cardWithStatus = MaintenanceTaskWithStatus(
    card = previewCard("Annual Inspection"),
    dueStatus = DueMetadata(
      nextDueEngine = 1250f,
      status = DueStatus.DUE_SOON,
    ),
  ),
)

@Preview
@Composable
fun PreviewTaskCardItemComplied() = TaskCardItem(
  cardWithStatus = MaintenanceTaskWithStatus(
    card = previewCard("Oil Change", "Every 50 hours"),
    dueStatus = DueMetadata(status = DueStatus.COMPLIED),
  ),
)

@Preview
@Composable
fun PreviewTaskCardItemOnCondition() = TaskCardItem(
  cardWithStatus = MaintenanceTaskWithStatus(
    card = previewCard("Prop Strike Inspection"),
    dueStatus = DueMetadata(isOnCondition = true, status = DueStatus.NORMAL),
  ),
)
