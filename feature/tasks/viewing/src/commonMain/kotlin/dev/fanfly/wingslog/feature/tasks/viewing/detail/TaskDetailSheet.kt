package dev.fanfly.wingslog.feature.tasks.viewing.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheetAction
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheetActionRow
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheetEditAction
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DataLogRowInfo
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentSection
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.compliance_type_ad_short
import wingslog.feature.tasks.sharedassets.generated.resources.compliance_type_sb_short
import wingslog.feature.tasks.sharedassets.generated.resources.create_work_log
import wingslog.feature.tasks.sharedassets.generated.resources.edit_task
import wingslog.feature.tasks.sharedassets.generated.resources.skip_this_cycle_option
import wingslog.feature.tasks.viewing.generated.resources.authority_reference_number
import wingslog.feature.tasks.viewing.generated.resources.no_maintenance_logs_for_task
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
  cardWithStatus: MaintenanceTaskWithStatus,
  logs: List<MaintenanceLog>,
  onDismiss: () -> Unit,
  onEditClick: (() -> Unit)?,
  onAttachmentTap: (Attachment) -> Unit = {},
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  dataLogs: Map<DataLogId, DataLogRowInfo>? = null,
  openError: String? = null,
  /**
   * Advancing the schedule. Not "Resolve": a task has no end state, only a next due date, so the
   * primary action is logging the work — with Skip beside it, or the cycle could not be advanced
   * without a log for work that never happened. Both null for a caller who may not change it.
   */
  onLogWorkClick: (() -> Unit)? = null,
  onSkipCycleClick: (() -> Unit)? = null,
  /**
   * The record's comment thread and the box it is written in, supplied by the host so this module
   * need not know about comments. [commentComposer] is pinned under the scrolling sheet.
   */
  comments: (@Composable () -> Unit)? = null,
  commentComposer: (@Composable () -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val card = cardWithStatus.card
  val dueStatus = cardWithStatus.dueStatus

  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    bottomBar = commentComposer,
    headerAction = onEditClick?.let {
      {
        DetailSheetEditAction(
          label = stringResource(SharedRes.string.edit_task),
          onClick = it,
        )
      }
    },
    headerSlot = {
      if (dueStatus.status == DueStatus.OVERDUE || dueStatus.status == DueStatus.DUE_SOON) {
        StatusBadge(dueStatus)
      }
      Text(
        text = card.title,
        style = MaterialTheme.typography.displaySmall,
      )
    },
  ) {

    // Line 1: compliance type badge (SB / AD), if present
    val typeLabel = when (card.type) {
      ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> stringResource(
        SharedRes.string.compliance_type_sb_short
      )

      ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> stringResource(
        SharedRes.string.compliance_type_ad_short
      )

      else -> null
    }
    if (typeLabel != null) {
      Text(
        text = typeLabel,
        style = MaterialTheme.typography.labelSmall,
        color = if (card.type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.background(
          if (card.type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE) MaterialTheme.colorScheme.errorContainer
          else MaterialTheme.colorScheme.primaryContainer,
          RoundedCornerShape(Spacing.badgeCornerRadius),
        )
          .padding(
            horizontal = Spacing.extraSmall, vertical = Spacing.extraSmall
          ),
      )
    }

    // Line 2: compliance authority · reference number (dot omitted if only one is present)
    val authority = card.compliance_authority.takeIf { it.isNotBlank() }
    val refNumber = card.reference_number.takeIf { it.isNotBlank() }
    val metaLine = when {
      authority != null && refNumber != null -> stringResource(
        ViewingRes.string.authority_reference_number,
        authority,
        refNumber,
      )

      authority != null -> authority
      refNumber != null -> refNumber
      else -> null
    }
    if (metaLine != null) {
      Text(
        text = metaLine,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Description text (notes and compliance details shown as plain body text)
    if (card.notes.isNotBlank()) {
      Text(
        text = card.notes,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.85f),
      )
    }
    if (card.compliance_details.isNotBlank()) {
      Text(
        text = card.compliance_details,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.85f),
      )
    }

    Spacer(Modifier.height(Spacing.medium))

    // Due date hero
    DueDateHero(dueStatus)

    if (
      onLogWorkClick != null && onSkipCycleClick != null && dueStatus.status != DueStatus.COMPLIED
    ) {
      DetailSheetActionRow(modifier = Modifier.padding(top = Spacing.small)) {
        DetailSheetAction(
          label = stringResource(SharedRes.string.skip_this_cycle_option),
          onClick = onSkipCycleClick,
        )
        DetailSheetAction(
          label = stringResource(
            SharedRes.string.create_work_log,
            LocalThingLexicon.current.logNoun.singular,
          ),
          onClick = onLogWorkClick,
          primary = true,
        )
      }
    }

    Spacer(Modifier.height(Spacing.large))

    AttachmentSection(
      attachments = card.attachments,
      onAttachmentTap = onAttachmentTap,
      syncStates = syncStates,
      dataLogs = dataLogs,
      openError = openError,
    )

    if (card.attachments.isNotEmpty()) {
      Spacer(modifier = Modifier.height(Spacing.large))
    }

    Text(
      text = LexiconFormatter.titleCasePlural(LocalThingLexicon.current.logNoun),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(modifier = Modifier.height(Spacing.small))

    if (logs.isEmpty()) {
      Text(
        text = stringResource(
          ViewingRes.string.no_maintenance_logs_for_task,
          LocalThingLexicon.current.logNoun.plural,
          LocalThingLexicon.current.taskNoun.singular,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      logs.forEach { log ->
        LogHistoryItem(log)
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.extraSmall))
      }
    }

    if (comments != null) {
      Spacer(Modifier.height(Spacing.large))
      comments()
    }
  }
}
