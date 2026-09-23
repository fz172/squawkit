package dev.fanfly.wingslog.feature.logs.viewing.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.ui.sheet.DetailSheet
import dev.fanfly.wingslog.core.ui.sheet.DetailSheetEditAction
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DataLogRowInfo
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentSection
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.logs.viewing.LogComponentBadge
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.edit_log
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.logs.sharedassets.generated.resources.Res as MaintenanceRes
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
  dataLogs: Map<DataLogId, DataLogRowInfo>? = null,
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
    headerAction = onEditClick?.let {
      {
        DetailSheetEditAction(
          label = stringResource(
            MaintenanceRes.string.edit_log,
            LocalThingLexicon.current.logNoun.singular,
          ),
          onClick = it,
        )
      }
    },
    headerSlot = {
      LogComponentBadge(log.component_type)
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
      dataLogs = dataLogs,
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
          // On a shared thing, being named as the technician does not mean you wrote the entry.
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
