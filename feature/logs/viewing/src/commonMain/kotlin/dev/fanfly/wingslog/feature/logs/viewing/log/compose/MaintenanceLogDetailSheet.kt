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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentSection
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.unit_hours
import wingslog.feature.logs.sharedassets.generated.resources.airframe_time_label
import wingslog.feature.logs.sharedassets.generated.resources.edit_log
import wingslog.feature.logs.sharedassets.generated.resources.engine_time_label
import wingslog.feature.logs.sharedassets.generated.resources.prop_time_label
import wingslog.feature.logs.viewing.generated.resources.affected_maintenance_tasks
import wingslog.feature.logs.viewing.generated.resources.no_tasks_linked
import wingslog.feature.logs.viewing.generated.resources.resolved_squawks
import wingslog.feature.logs.viewing.generated.resources.unknown_squawk
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.log_assigned_by
import wingslog.feature.logs.sharedassets.generated.resources.log_assigned_by_unknown
import wingslog.feature.logs.sharedassets.generated.resources.log_unverified_technician
import wingslog.feature.logs.sharedassets.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.viewing.generated.resources.Res as ViewingRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogDetailSheet(
  log: MaintenanceLog,
  availableCards: List<MaintenanceTask>,
  onDismiss: () -> Unit,
  authorship: LogAuthorship = LogAuthorship.Unknown,
  onEditClick: (() -> Unit)?,
  onAttachmentTap: (Attachment) -> Unit = {},
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  openError: String? = null,
  onTaskClick: ((String) -> Unit)? = null,
  availableSquawks: List<Squawk> = emptyList(),
  onSquawkClick: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val dateStr = log.timestamp?.toLocalDate()
    ?.toDisplayFormat()
    ?: stringResource(SharedTaskRes.string.unknown_date)

  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    actionSlot = {
      if (onEditClick != null) {
        TextButton(onClick = onEditClick) {
          Text(stringResource(MaintenanceRes.string.edit_log))
        }
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
    HorizontalDivider(
      color = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = 0.5f
      )
    )

    // Affected Maintenance Tasks
    AffectedTasksSection(
      log = log,
      availableCards = availableCards,
      onTaskClick = onTaskClick,
    )

    // Resolved Squawks (only when this entry addressed some)
    if (log.squawk_ids.isNotEmpty()) {
      ResolvedSquawksSection(
        log = log,
        availableSquawks = availableSquawks,
        onSquawkClick = onSquawkClick,
      )
    }

    // Attachments
    AttachmentSection(
      attachments = log.attachments,
      onAttachmentTap = onAttachmentTap,
      syncStates = syncStates,
      openError = openError,
    )

    // Footer: technician (if enabled) | date
    HorizontalDivider(
      color = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = 0.5f
      )
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = Spacing.small),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val techName = log.technician?.name?.takeIf { it.isNotBlank() }
      if (techName != null) {
        // fill = false keeps the column at its natural width, but caps it: a long name wraps inside
        // the column instead of pushing the date off the end of the row.
        Column(modifier = Modifier.weight(1f, fill = false)) {
          Text(
            text = techName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          // On a shared aircraft, being named as the technician does not mean you wrote the entry.
          // Say which it is — unforgeably, from the envelope's writer_uid (§7.5).
          AuthorshipLine(authorship)
        }
      } else {
        Spacer(Modifier.height(Spacing.none))
      }
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = Spacing.small),
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
    ComponentType.COMPONENT_AIRFRAME -> airframeLabel to log.airframe_time
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
        modifier = Modifier.alignByBaseline()
          .padding(start = Spacing.extraSmall),
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
        LinkedEntityRow(
          title = title,
          icon = Icons.Default.Build,
          onClick = onTaskClick?.let { cb -> { cb(cardId) } },
        )
      }
    }
  }
}

@Composable
private fun ResolvedSquawksSection(
  log: MaintenanceLog,
  availableSquawks: List<Squawk>,
  onSquawkClick: ((String) -> Unit)?,
) {
  Text(
    text = stringResource(ViewingRes.string.resolved_squawks),
    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
  )

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    log.squawk_ids.forEach { squawkId ->
      val squawk = availableSquawks.find { it.id == squawkId }
      val title = squawk?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(ViewingRes.string.unknown_squawk, squawkId)
      LinkedEntityRow(
        title = title,
        icon = Icons.Default.Warning,
        onClick = onSquawkClick?.let { cb -> { cb(squawkId) } },
      )
    }
  }
}

@Composable
private fun LinkedEntityRow(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        imageVector = icon,
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

/**
 * What we can and cannot say about the name above this line.
 *
 * It speaks up in two cases. [LogAuthorship.Assigned] — someone other than the named technician
 * wrote the entry. [LogAuthorship.Unverifiable] — the name was typed by hand and belongs to no
 * account, so nothing stands behind it; a typed name should not sit there looking as settled as a
 * signed one.
 *
 * It stays silent in two. [LogAuthorship.SelfSigned] is the ordinary case — the technician wrote up
 * their own work — and remarking on it would make the exception look like the rule.
 * [LogAuthorship.Unknown] is a revision written before authorship was recorded: it proves nothing
 * either way, and casting doubt on an old entry we simply have no data for would be a lie.
 *
 * The technician's name is the line directly above this one, so it is never repeated here.
 */
@Composable
private fun AuthorshipLine(authorship: LogAuthorship) {
  val text = when (authorship) {
    is LogAuthorship.Assigned -> authorship.authorName?.let { author ->
      stringResource(MaintenanceRes.string.log_assigned_by, author)
    } ?: stringResource(MaintenanceRes.string.log_assigned_by_unknown)

    is LogAuthorship.Unverifiable ->
      stringResource(MaintenanceRes.string.log_unverified_technician)

    is LogAuthorship.SelfSigned, LogAuthorship.Unknown -> return
  }
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
