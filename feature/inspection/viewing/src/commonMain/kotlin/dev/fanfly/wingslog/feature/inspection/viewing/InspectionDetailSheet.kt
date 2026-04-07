package dev.fanfly.wingslog.feature.inspection.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.attachments.viewing.AttachmentSection
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.feature.inspection.model.DueMetadata
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.dash
import wingslog.feature.inspection.sharedassets.generated.resources.compliance_authority
import wingslog.feature.inspection.sharedassets.generated.resources.compliance_type_ad_short
import wingslog.feature.inspection.sharedassets.generated.resources.compliance_type_sb_short
import wingslog.feature.inspection.sharedassets.generated.resources.due_soon
import wingslog.feature.inspection.sharedassets.generated.resources.edit_inspection
import wingslog.feature.inspection.sharedassets.generated.resources.engine_format
import wingslog.feature.inspection.sharedassets.generated.resources.overdue
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_date
import wingslog.feature.inspection.viewing.generated.resources.compliance_details
import wingslog.feature.inspection.viewing.generated.resources.complied
import wingslog.feature.inspection.viewing.generated.resources.due_date
import wingslog.feature.inspection.viewing.generated.resources.due_engine
import wingslog.feature.inspection.viewing.generated.resources.due_soon_date
import wingslog.feature.inspection.viewing.generated.resources.due_soon_date_engine
import wingslog.feature.inspection.viewing.generated.resources.due_soon_engine
import wingslog.feature.inspection.viewing.generated.resources.maintenance_history
import wingslog.feature.inspection.viewing.generated.resources.no_maintenance_logs_for_inspection
import wingslog.feature.inspection.viewing.generated.resources.on_condition
import wingslog.feature.inspection.viewing.generated.resources.overdue_was
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.inspection.viewing.generated.resources.Res as ViewingRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailSheet(
  cardWithStatus: InspectionCardWithStatus,
  logs: List<MaintenanceLog>,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  onAttachmentTap: (Attachment) -> Unit = {},
  downloadingIds: Set<String> = emptySet(),
  modifier: Modifier = Modifier,
) {
  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    actionSlot = {
      TextButton(onClick = onEditClick) {
        Text(stringResource(SharedRes.string.edit_inspection))
      }
    },
    headerSlot = {
      val typeLabel = when (cardWithStatus.card.type) {
        ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> stringResource(SharedRes.string.compliance_type_sb_short)
        ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> stringResource(
          SharedRes.string.compliance_type_ad_short
        )

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
  ) {
    DueStatusChip(cardWithStatus.dueStatus)

    if (cardWithStatus.card.compliance_authority.isNotBlank()) {
      Spacer(Modifier.height(Spacing.medium))
      Text(
        text = stringResource(SharedRes.string.compliance_authority),
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
        text = stringResource(ViewingRes.string.compliance_details),
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

    AttachmentSection(
      attachments = cardWithStatus.card.attachments,
      onAttachmentTap = onAttachmentTap,
      downloadingIds = downloadingIds,
    )

    if (cardWithStatus.card.attachments.isNotEmpty()) {
      Spacer(modifier = Modifier.height(Spacing.large))
    }

    Text(
      text = stringResource(ViewingRes.string.maintenance_history),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(Spacing.small))

    if (logs.isEmpty()) {
      Text(
        text = stringResource(ViewingRes.string.no_maintenance_logs_for_inspection),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      logs.forEach { log ->
        LogHistoryItem(log)
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.extraSmall))
      }
    }
  }
}

@Composable
private fun DueStatusChip(dueStatus: DueMetadata) {
  val (label, color) = when {
    dueStatus.status == DueStatus.COMPLIED -> stringResource(ViewingRes.string.complied) to StatusOk
    dueStatus.isOnCondition -> stringResource(ViewingRes.string.on_condition) to MaterialTheme.colorScheme.onSurfaceVariant
    dueStatus.status == DueStatus.OVERDUE -> {
      val dateStr = dueStatus.nextDueDate?.toDisplayFormat() ?: ""
      (if (dateStr.isNotBlank()) stringResource(
        ViewingRes.string.overdue_was, dateStr
      ) else stringResource(SharedRes.string.overdue)) to MaterialTheme.colorScheme.error
    }

    dueStatus.status == DueStatus.DUE_SOON -> {
      val dateStr = dueStatus.nextDueDate?.toDisplayFormat()
      val engineStr = dueStatus.nextDueEngine?.toDouble()?.formatToOneDecimalPlace()
      when {
        dateStr != null && engineStr != null -> stringResource(
          ViewingRes.string.due_soon_date_engine,
          dateStr,
          engineStr
        )

        dateStr != null -> stringResource(ViewingRes.string.due_soon_date, dateStr)
        engineStr != null -> stringResource(ViewingRes.string.due_soon_engine, engineStr)
        else -> stringResource(SharedRes.string.due_soon)
      } to StatusWarning
    }

    dueStatus.nextDueDate != null -> stringResource(
      ViewingRes.string.due_date, dueStatus.nextDueDate!!.toDisplayFormat()
    ) to StatusOk

    dueStatus.nextDueEngine != null -> stringResource(
      ViewingRes.string.due_engine,
      dueStatus.nextDueEngine!!.toDouble().formatToOneDecimalPlace()
    ) to StatusOk

    else -> stringResource(CoreRes.string.dash) to MaterialTheme.colorScheme.onSurfaceVariant
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
    stringResource(SharedRes.string.unknown_date)
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
        text = stringResource(
          SharedRes.string.engine_format, log.engine_hour.formatToOneDecimalPlace()
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

