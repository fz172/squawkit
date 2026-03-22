package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import wingslog.feature.aircraft.generated.resources.done
import wingslog.feature.aircraft.generated.resources.due_date
import wingslog.feature.aircraft.generated.resources.due_tach
import wingslog.feature.aircraft.generated.resources.edit_inspection
import wingslog.feature.aircraft.generated.resources.maintenance_history
import wingslog.feature.aircraft.generated.resources.no_maintenance_logs_for_inspection
import wingslog.feature.aircraft.generated.resources.on_condition
import wingslog.feature.aircraft.generated.resources.overdue
import wingslog.feature.aircraft.generated.resources.overdue_was
import wingslog.feature.aircraft.generated.resources.tach_format
import wingslog.feature.aircraft.generated.resources.unknown_date
// dateFormatter removed
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailSheet(
  cardWithStatus: dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus,
  logs: List<MaintenanceLog>,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = cardWithStatus.card.title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold
        )
        Row {
          IconButton(onClick = onEditClick) {
            Icon(
              Icons.Default.Edit,
              contentDescription = cmpStringResource(AircraftRes.string.edit_inspection)
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(
              Icons.Default.Close,
              contentDescription = cmpStringResource(AircraftRes.string.done)
            )
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      DueStatusChip(cardWithStatus.dueStatus)

      if (cardWithStatus.card.notes.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
          text = cardWithStatus.card.notes,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(16.dp))

      Text(
        text = cmpStringResource(AircraftRes.string.maintenance_history),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )

      Spacer(Modifier.height(8.dp))

      if (logs.isEmpty()) {
        Text(
          text = cmpStringResource(AircraftRes.string.no_maintenance_logs_for_inspection),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        logs.forEach { log ->
          LogHistoryItem(log)
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
      }

      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
private fun DueStatusChip(dueStatus: DueStatus) {
  val (label, color) = when {
    dueStatus.isOnCondition -> cmpStringResource(AircraftRes.string.on_condition) to MaterialTheme.colorScheme.onSurfaceVariant
    dueStatus.isOverdue -> {
      val dateStr = dueStatus.nextDueDate?.toDisplayFormat() ?: ""
      (if (dateStr.isNotBlank()) cmpStringResource(
        AircraftRes.string.overdue_was,
        dateStr
      ) else cmpStringResource(AircraftRes.string.overdue)) to MaterialTheme.colorScheme.error
    }

    dueStatus.nextDueDate != null -> cmpStringResource(
      AircraftRes.string.due_date,
      dueStatus.nextDueDate?.toDisplayFormat() ?: ""
    ) to StatusOk

    dueStatus.nextDueTach != null -> cmpStringResource(
      AircraftRes.string.due_tach,
      (dueStatus.nextDueTach ?: 0f).toDouble().formatToOneDecimalPlace()
    ) to StatusOk

    else -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
  }
  AssistChip(
    onClick = {},
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    colors = AssistChipDefaults.assistChipColors(labelColor = color),
  )
}

@Composable
private fun LogHistoryItem(log: MaintenanceLog) {
  val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L) {
    log.timestamp!!.toLocalDate().toDisplayFormat()
  } else {
    cmpStringResource(AircraftRes.string.unknown_date)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = cmpStringResource(AircraftRes.string.tach_format, log.tach_time.formatToOneDecimalPlace()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Text(
      text = log.work_description,
      style = MaterialTheme.typography.bodyMedium
    )
  }
}
