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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.feature.aircraft.R
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus
import kotlinx.datetime.toJavaLocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

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
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_inspection))
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.done))
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      DueStatusChip(cardWithStatus.dueStatus)

      Spacer(Modifier.height(16.dp))

      Text(
        text = stringResource(R.string.maintenance_history),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )

      Spacer(Modifier.height(8.dp))

      if (logs.isEmpty()) {
        Text(
          text = stringResource(R.string.no_maintenance_logs_for_inspection),
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
    dueStatus.isOnCondition -> stringResource(R.string.on_condition) to MaterialTheme.colorScheme.onSurfaceVariant
    dueStatus.isOverdue -> {
      val dateStr = dueStatus.nextDueDate?.toJavaLocalDate()?.format(dateFormatter) ?: ""
      (if (dateStr.isNotBlank()) stringResource(
        R.string.overdue_was,
        dateStr
      ) else stringResource(R.string.overdue)) to MaterialTheme.colorScheme.error
    }

    dueStatus.nextDueDate != null -> stringResource(
      R.string.due_date,
      dueStatus.nextDueDate?.toJavaLocalDate()?.format(dateFormatter) ?: ""
    ) to StatusOk

    dueStatus.nextDueTach != null -> stringResource(
      R.string.due_tach,
      String.format(Locale.getDefault(), "%.1f", dueStatus.nextDueTach ?: 0f)
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
  val dateStr = if ((log.timestamp?.epochSecond ?: 0L) > 0L) {
    Instant.ofEpochSecond(log.timestamp?.epochSecond ?: 0L)
      .atZone(ZoneId.systemDefault())
      .toLocalDate()
      .format(dateFormatter)
  } else {
    stringResource(R.string.unknown_date)
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
        text = stringResource(R.string.tach_format, log.tach_time),
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
