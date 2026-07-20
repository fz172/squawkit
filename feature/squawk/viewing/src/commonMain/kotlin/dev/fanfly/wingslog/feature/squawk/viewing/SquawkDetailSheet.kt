package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.DetailSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentSection
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.sharedassets.toLabel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.dismissed_label
import wingslog.feature.squawk.sharedassets.generated.resources.edit_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.no_work_recorded
import wingslog.feature.squawk.sharedassets.generated.resources.reported
import wingslog.feature.squawk.sharedassets.generated.resources.work_history

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquawkDetailSheet(
  item: SquawkWithStatus,
  addressingLog: MaintenanceLog?,
  onDismiss: () -> Unit,
  onEditClick: (() -> Unit)?,
  /** Jump to the addressing work log in the Logs tab. Null renders the log statically (no affordance). */
  onLogClick: ((logId: String) -> Unit)? = null,
  onAttachmentTap: (Attachment) -> Unit = {},
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  openError: String? = null,
  modifier: Modifier = Modifier,
) {
  val squawk = item.squawk

  DetailSheet(
    onDismiss = onDismiss,
    modifier = modifier,
    actionSlot = {
      if (onEditClick != null) {
        TextButton(onClick = onEditClick) {
          Text(stringResource(Res.string.edit_squawk))
        }
      }
    },
    headerSlot = {
      PriorityBadge(item)
    },
  ) {
    Text(
      text = squawk.title,
      style = MaterialTheme.typography.displaySmall,
    )

    // Reported date
    if ((squawk.created_at?.getEpochSecond() ?: 0L) > 0L) {
      val dateStr = squawk.created_at!!.toLocalDate()
        .toDisplayFormat()
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
          text = stringResource(Res.string.reported),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = dateStr,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    // Description
    if (squawk.description.isNotBlank()) {
      Text(
        text = squawk.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.9f),
      )
    }

    Spacer(Modifier.height(Spacing.medium))

    // Work History section
    Text(
      text = stringResource(Res.string.work_history),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )

    Spacer(Modifier.height(Spacing.small))

    when {
      addressingLog != null -> LogHistoryRow(
        log = addressingLog,
        onClick = onLogClick?.let { jump -> { jump(addressingLog.id) } },
      )
      item.status == SquawkStatus.DISMISSED -> DismissedHistoryRow(item)
      else -> Text(
        text = stringResource(Res.string.no_work_recorded),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(Spacing.large))
    AttachmentSection(
      attachments = squawk.attachments,
      onAttachmentTap = onAttachmentTap,
      syncStates = syncStates,
      openError = openError,
    )
  }
}

@Composable
private fun DismissedHistoryRow(item: SquawkWithStatus) {
  val squawk = item.squawk
  val reasonLabel = squawk.dismiss_reason.toLabel()
  val dateStr = squawk.dismissed_at
    ?.takeIf { it.getEpochSecond() > 0L }
    ?.toLocalDate()
    ?.toDisplayFormat()

  Column(
    modifier = Modifier.fillMaxWidth()
      .padding(vertical = Spacing.extraSmall),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
      text = stringResource(Res.string.dismissed_label, reasonLabel),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (dateStr != null) {
      Text(
        text = dateStr,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  HorizontalDivider(modifier = Modifier.padding(top = Spacing.extraSmall))
}

@Composable
private fun LogHistoryRow(
  log: MaintenanceLog,
  onClick: (() -> Unit)?,
) {
  val dateStr = if ((log.timestamp?.getEpochSecond() ?: 0L) > 0L)
    log.timestamp!!.toLocalDate()
      .toDisplayFormat()
  else ""

  Row(
    modifier = Modifier.fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      if (dateStr.isNotEmpty()) {
        Text(
          text = dateStr,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
        )
      }
      Text(
        text = log.work_description,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    if (onClick != null) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  HorizontalDivider(modifier = Modifier.padding(top = Spacing.extraSmall))
}
