package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentSection
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.unit_hours
import wingslog.feature.logs.sharedassets.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.sharedassets.generated.resources.airframe_time_label
import wingslog.feature.logs.sharedassets.generated.resources.edit_log
import wingslog.feature.logs.sharedassets.generated.resources.engine_time_label
import wingslog.feature.logs.sharedassets.generated.resources.prop_time_label
import wingslog.feature.logs.viewing.generated.resources.Res as ViewingRes
import wingslog.feature.logs.viewing.generated.resources.affected_maintenance_tasks
import wingslog.feature.logs.viewing.generated.resources.no_tasks_linked
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogDetailSheet(
  log: MaintenanceLog,
  availableCards: List<MaintenanceTask>,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit,
  onAttachmentTap: (Attachment) -> Unit = {},
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  openError: String? = null,
  onTaskClick: ((String) -> Unit)? = null,
  technicianEnabled: Boolean = true,
  attachmentEnabled: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val dateStr = log.timestamp?.toLocalDate()?.toDisplayFormat()
    ?: stringResource(SharedTaskRes.string.unknown_date)

  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    actionSlot = {
      TextButton(onClick = onEditClick) {
        Text(stringResource(MaintenanceRes.string.edit_log))
      }
    },
    headerSlot = {
      ComponentTypeBadge(log.component_type)
    },
  ) {
    // Hero metric
    SheetHeroMetric(log)

    // Work description
    if (log.work_description.isNotBlank()) {
      Text(
        text = log.work_description,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(Spacing.large))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    // Affected Maintenance Tasks
    AffectedTasksSection(
      log = log,
      availableCards = availableCards,
      onTaskClick = onTaskClick,
    )

    // Attachments
    if (attachmentEnabled) {
      AttachmentSection(
        attachments = log.attachments,
        onAttachmentTap = onAttachmentTap,
        syncStates = syncStates,
        openError = openError,
      )
    }

    // Footer: technician (if enabled) | date
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = Spacing.small),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (technicianEnabled) {
        val techName = log.technician?.name?.takeIf { it.isNotBlank() }
        if (techName != null) {
          Text(
            text = techName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        } else {
          Spacer(Modifier.height(Spacing.none))
        }
      } else {
        Spacer(Modifier.height(Spacing.none))
      }
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun SheetHeroMetric(log: MaintenanceLog) {
  val engineLabel = stringResource(MaintenanceRes.string.engine_time_label)
  val airframeLabel = stringResource(MaintenanceRes.string.airframe_time_label)
  val propLabel = stringResource(MaintenanceRes.string.prop_time_label)

  val (label, value) = when (log.component_type) {
    ComponentType.COMPONENT_ENGINE -> engineLabel to log.engine_hour
    ComponentType.COMPONENT_AIRFRAME,
    ComponentType.COMPONENT_AVIONICS,
      -> airframeLabel to log.airframe_time

    ComponentType.COMPONENT_PROPELLER -> propLabel to log.prop_time
    else -> when {
      log.engine_hour > 0.0 -> engineLabel to log.engine_hour
      log.airframe_time > 0.0 -> airframeLabel to log.airframe_time
      else -> propLabel to log.prop_time
    }
  }

  Column {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.extraSmall))
    Row {
      Text(
        text = value.formatToOneDecimalPlace(),
        style = WingslogTypography.heroDisplay,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.alignByBaseline(),
      )
      Text(
        text = stringResource(CoreRes.string.unit_hours),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.alignByBaseline().padding(start = Spacing.extraSmall),
      )
    }
  }
}

@Composable
private fun AffectedTasksSection(
  log: MaintenanceLog,
  availableCards: List<MaintenanceTask>,
  onTaskClick: ((String) -> Unit)?,
) {
  Text(
    text = stringResource(ViewingRes.string.affected_maintenance_tasks),
    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
  )

  if (log.inspection_ids.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(Spacing.chipCornerRadius),
        )
        .padding(Spacing.medium),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(ViewingRes.string.no_tasks_linked),
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      log.inspection_ids.forEach { cardId ->
        val card = availableCards.find { it.id == cardId }
        val title = card?.title ?: stringResource(
          SharedTaskRes.string.unknown_task,
          cardId
        )
        LinkedTaskRow(
          title = title,
          onClick = onTaskClick?.let { cb -> { cb(cardId) } },
        )
      }
    }
  }
}

@Composable
private fun LinkedTaskRow(
  title: String,
  onClick: (() -> Unit)?,
) {
  Surface(
    onClick = onClick ?: {},
    enabled = onClick != null,
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = Spacing.medium,
          vertical = Spacing.medium
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        imageVector = Icons.Default.Build,
        contentDescription = null,
        modifier = Modifier.size(Spacing.large),
        tint = MaterialTheme.colorScheme.secondary
      )
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.padding(
          start = Spacing.extraLarge,
          end = Spacing.medium
        ),
      )
    }
  }
}
