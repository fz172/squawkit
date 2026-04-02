package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.aircraft.inspection.data.DueMetadata
import dev.fanfly.wingslog.feature.aircraft.inspection.data.DueStatus
import dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus
import wingslog.feature.aircraft.generated.resources.done
import wingslog.feature.aircraft.generated.resources.due_date
import wingslog.feature.aircraft.generated.resources.due_engine
import wingslog.feature.aircraft.generated.resources.edit_inspection
import wingslog.feature.aircraft.generated.resources.engine_format
import wingslog.feature.aircraft.generated.resources.maintenance_history
import wingslog.feature.aircraft.generated.resources.no_maintenance_logs_for_inspection
import wingslog.feature.aircraft.generated.resources.on_condition
import wingslog.feature.aircraft.generated.resources.overdue
import wingslog.feature.aircraft.generated.resources.overdue_was
import wingslog.feature.aircraft.generated.resources.unknown_date
import wingslog.feature.aircraft.inspection.generated.resources.compliance_authority
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailSheet(
  cardWithStatus: InspectionCardWithStatus,
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
      modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.extraLarge)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          val typeLabel = when (cardWithStatus.card.type) {
            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> "SB"
            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> "AD"
            else -> null
          }
          if (typeLabel != null) {
            Text(
              text = typeLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (cardWithStatus.card.type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE) {
                MaterialTheme.colorScheme.onErrorContainer
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer
              },
              modifier = Modifier
                .background(
                  if (cardWithStatus.card.type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE) {
                    MaterialTheme.colorScheme.errorContainer
                  } else {
                    MaterialTheme.colorScheme.primaryContainer
                  },
                  RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.height(Spacing.tiny))
          }
          Text(
            text = cardWithStatus.card.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
          )
          if (cardWithStatus.card.reference_number.isNotBlank()) {
            Text(
              text = cardWithStatus.card.reference_number,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Row {
          IconButton(onClick = onEditClick) {
            Icon(
              Icons.Default.Edit,
              contentDescription = cmpStringResource(AircraftRes.string.edit_inspection)
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(
              Icons.Default.Close, contentDescription = cmpStringResource(AircraftRes.string.done)
            )
          }
        }
      }

      Spacer(Modifier.height(Spacing.small))

      DueStatusChip(cardWithStatus.dueStatus)

      if (cardWithStatus.card.compliance_authority.isNotBlank()) {
        Spacer(Modifier.height(Spacing.medium))
        Text(
          text = cmpStringResource(InspectionRes.string.compliance_authority),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = cardWithStatus.card.compliance_authority,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }

      if (cardWithStatus.card.compliance_details.isNotBlank()) {
        Spacer(Modifier.height(Spacing.large))
        Text(
          text = "Compliance Details",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = cardWithStatus.card.compliance_details,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      if (cardWithStatus.card.notes.isNotBlank()) {
        Spacer(Modifier.height(Spacing.medium))
        Text(
          text = cardWithStatus.card.notes,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(Spacing.large))

      Text(
        text = cmpStringResource(AircraftRes.string.maintenance_history),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )

      Spacer(modifier = Modifier.height(Spacing.small))

      if (logs.isEmpty()) {
        Text(
          text = cmpStringResource(AircraftRes.string.no_maintenance_logs_for_inspection),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        logs.forEach { log ->
          LogHistoryItem(log)
          HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.extraSmall))
        }
      }

      Spacer(modifier = Modifier.height(Spacing.huge))
    }
  }
}

@Composable
private fun DueStatusChip(dueStatus: DueMetadata) {
  val (label, color) = when {
    dueStatus.status == DueStatus.COMPLIED -> "Complied" to StatusOk
    dueStatus.isOnCondition -> cmpStringResource(AircraftRes.string.on_condition) to MaterialTheme.colorScheme.onSurfaceVariant
    dueStatus.status == DueStatus.OVERDUE -> {
      val dateStr = dueStatus.nextDueDate?.toDisplayFormat() ?: ""
      (if (dateStr.isNotBlank()) cmpStringResource(
        AircraftRes.string.overdue_was, dateStr
      ) else cmpStringResource(AircraftRes.string.overdue)) to MaterialTheme.colorScheme.error
    }

    dueStatus.status == DueStatus.DUE_SOON -> {
      val dateStr = dueStatus.nextDueDate?.toDisplayFormat()
      val engineStr = dueStatus.nextDueEngine?.toDouble()?.formatToOneDecimalPlace()
      when {
        dateStr != null && engineStr != null -> "Due Soon: $dateStr / $engineStr hrs"
        dateStr != null -> "Due Soon: $dateStr"
        engineStr != null -> "Due Soon: @ $engineStr hrs"
        else -> "Due Soon"
      } to StatusWarning
    }

    dueStatus.nextDueDate != null -> cmpStringResource(
      AircraftRes.string.due_date, dueStatus.nextDueDate!!.toDisplayFormat()
    ) to StatusOk

    dueStatus.nextDueEngine != null -> cmpStringResource(
      AircraftRes.string.due_engine, dueStatus.nextDueEngine!!.toDouble().formatToOneDecimalPlace()
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
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
      )
      Text(
        text = cmpStringResource(
          AircraftRes.string.engine_format, log.engine_hour.formatToOneDecimalPlace()
        ),

        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Text(
      text = log.work_description, style = MaterialTheme.typography.bodyMedium
    )
  }
}
