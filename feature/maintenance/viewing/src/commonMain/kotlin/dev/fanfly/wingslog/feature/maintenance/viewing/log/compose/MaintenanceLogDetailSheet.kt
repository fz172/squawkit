package dev.fanfly.wingslog.feature.maintenance.viewing.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.attachments.viewing.AttachmentSection
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.maintenance.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.inspection.sharedassets.generated.resources.inspection_work
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_date
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_inspection
import wingslog.feature.maintenance.sharedassets.generated.resources.airframe_time_label
import wingslog.feature.maintenance.sharedassets.generated.resources.edit_log
import wingslog.feature.maintenance.sharedassets.generated.resources.engine_time_label
import wingslog.feature.maintenance.sharedassets.generated.resources.prop_time_label
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as MaintenanceRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogDetailSheet(
  log: MaintenanceLog,
  availableCards: List<InspectionCard>,
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
        Text(stringResource(MaintenanceRes.string.edit_log))
      }
    },
    headerSlot = {
      val dateStr = log.timestamp?.toLocalDate()?.toDisplayFormat()
        ?: stringResource(SharedInspectionRes.string.unknown_date)
      Text(
        text = dateStr,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.height(40.dp).wrapContentHeight(Alignment.CenterVertically)
      )
    }
  ) {
    // Work Description
    if (log.work_description.isNotBlank()) {
      Text(
        text = log.work_description,
        style = MaterialTheme.typography.bodyLarge,
      )
      Spacer(Modifier.height(Spacing.medium))
    }

    // Hours — each as its own inline row
    if (log.engine_hour > 0.0) {
      DetailRow(
        label = stringResource(MaintenanceRes.string.engine_time_label),
        value = log.engine_hour.formatToOneDecimalPlace(),
      )
    }
    if (log.airframe_time > 0.0) {
      DetailRow(
        label = stringResource(MaintenanceRes.string.airframe_time_label),
        value = log.airframe_time.formatToOneDecimalPlace(),
      )
    }
    if (log.prop_time > 0.0) {
      DetailRow(
        label = stringResource(MaintenanceRes.string.prop_time_label),
        value = log.prop_time.formatToOneDecimalPlace(),
      )
    }

    // Component / serial
    if (log.component_type != MaintenanceLog.ComponentType.UNKNOWN) {
      DetailRow(
        label = log.component_type.displayName(),
        value = log.component_serial,
      )
    }

    // Inspection items
    if (log.inspection_ids.isNotEmpty()) {
      Spacer(Modifier.height(Spacing.small))
      Text(
        text = stringResource(SharedInspectionRes.string.inspection_work),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
        log.inspection_ids.forEach { cardId ->
          val card = availableCards.find { it.id == cardId }
          val title =
            card?.title ?: stringResource(SharedInspectionRes.string.unknown_inspection, cardId)
          Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.tiny),
          ) {
            Text(
              text = "·",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = title,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(Spacing.small))

    // Attachments
    AttachmentSection(
      attachments = log.attachments,
      onAttachmentTap = onAttachmentTap,
      downloadingIds = downloadingIds,
    )
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "$label:",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
  }
}
