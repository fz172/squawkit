package dev.fanfly.wingslog.feature.inspection.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import wingslog.core.ui.generated.resources.dash
import wingslog.feature.inspection.viewing.generated.resources.complied
import wingslog.feature.inspection.viewing.generated.resources.due_date
import wingslog.feature.inspection.viewing.generated.resources.due_engine
import wingslog.feature.inspection.viewing.generated.resources.on_condition
import wingslog.feature.inspection.viewing.generated.resources.overdue
import wingslog.feature.inspection.viewing.generated.resources.overdue_was
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.inspection.viewing.generated.resources.Res as ViewingRes

@Composable
fun InspectionCardItem(
  cardWithStatus: InspectionCardWithStatus,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val status = cardWithStatus.dueStatus.status
  val statusColor = when (status) {
    DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
    DueStatus.DUE_SOON -> StatusWarning
    DueStatus.COMPLIED -> StatusOk
    DueStatus.NORMAL -> {
      if (cardWithStatus.dueStatus.isOnCondition) MaterialTheme.colorScheme.onSurfaceVariant
      else StatusOk
    }
  }
  val statusText = when {
    status == DueStatus.COMPLIED -> cmpStringResource(ViewingRes.string.complied)
    cardWithStatus.dueStatus.isOnCondition -> cmpStringResource(ViewingRes.string.on_condition)
    status == DueStatus.OVERDUE -> {
      val dateStr = cardWithStatus.dueStatus.nextDueDate?.toDisplayFormat()
      if (dateStr != null) cmpStringResource(ViewingRes.string.overdue_was, dateStr)
      else cmpStringResource(ViewingRes.string.overdue)
    }

    cardWithStatus.dueStatus.nextDueDate != null -> cmpStringResource(
      ViewingRes.string.due_date,
      cardWithStatus.dueStatus.nextDueDate!!.toDisplayFormat()
    )

    cardWithStatus.dueStatus.nextDueEngine != null -> cmpStringResource(
      ViewingRes.string.due_engine,
      cardWithStatus.dueStatus.nextDueEngine!!.toDouble().formatToOneDecimalPlace()
    )

    else -> cmpStringResource(CoreRes.string.dash)
  }
  val icon = when (status) {
    DueStatus.OVERDUE -> Icons.Filled.Error
    DueStatus.COMPLIED -> Icons.Default.CheckCircle
    else -> Icons.Default.CalendarToday
  }
  InspectionCard(
    title = cardWithStatus.card.title,
    status = statusText,
    icon = icon,
    statusColor = statusColor,
    isOverdue = status == DueStatus.OVERDUE,
    onClick = onClick,
    modifier = modifier,
  )
}