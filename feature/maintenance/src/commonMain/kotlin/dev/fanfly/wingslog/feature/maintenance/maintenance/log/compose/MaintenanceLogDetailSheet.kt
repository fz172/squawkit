package dev.fanfly.wingslog.feature.maintenance.maintenance.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.attachments.viewing.AttachmentSection
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.maintenance.maintenance.util.displayName
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.inspection.sharedassets.generated.resources.inspection_work
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_date
import wingslog.feature.inspection.sharedassets.generated.resources.unknown_inspection
import wingslog.feature.maintenance.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.generated.resources.airframe_time_label
import wingslog.feature.maintenance.generated.resources.edit_log
import wingslog.feature.maintenance.generated.resources.engine_time_label
import wingslog.feature.maintenance.generated.resources.maintenance_date
import wingslog.feature.maintenance.generated.resources.prop_time_label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogDetailSheet(
  log: MaintenanceLog,
  availableCards: List<InspectionCard>,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  onAttachmentTap: (Attachment) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.extraLarge)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      // Work description leads, Edit button top-right
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Text(
          text = log.work_description,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier
            .weight(1f)
            .padding(end = Spacing.small),
        )
        TextButton(onClick = onEditClick) {
          Text(cmpStringResource(MaintenanceRes.string.edit_log))
        }
      }

      // Date
      val dateStr = log.timestamp?.toLocalDate()?.toDisplayFormat()
        ?: cmpStringResource(SharedInspectionRes.string.unknown_date)
      DetailRow(
        label = cmpStringResource(MaintenanceRes.string.maintenance_date),
        value = dateStr,
      )

      // Hours — each as its own inline row
      if (log.engine_hour > 0.0) {
        DetailRow(
          label = cmpStringResource(MaintenanceRes.string.engine_time_label),
          value = log.engine_hour.formatToOneDecimalPlace(),
        )
      }
      if (log.airframe_time > 0.0) {
        DetailRow(
          label = cmpStringResource(MaintenanceRes.string.airframe_time_label),
          value = log.airframe_time.formatToOneDecimalPlace(),
        )
      }
      if (log.prop_time > 0.0) {
        DetailRow(
          label = cmpStringResource(MaintenanceRes.string.prop_time_label),
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
          text = cmpStringResource(SharedInspectionRes.string.inspection_work),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
          log.inspection_ids.forEach { cardId ->
            val card = availableCards.find { it.id == cardId }
            val title =
              card?.title ?: cmpStringResource(SharedInspectionRes.string.unknown_inspection, cardId)
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
      )

      Spacer(Modifier.height(Spacing.huge))
    }
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
