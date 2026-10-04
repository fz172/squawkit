package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.form.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DataLogRowInfo
import dev.fanfly.wingslog.feature.attachment.model.isFile
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.attachment.sharedassets.generated.resources.Res
import wingslog.feature.attachment.sharedassets.generated.resources.attachments
import wingslog.feature.attachment.sharedassets.generated.resources.open_failed

/**
 * Read-only list of attachments shown on detail views.
 * Hidden when [attachments] is empty (per PRD F3/F4).
 */
@Composable
fun AttachmentSection(
  attachments: List<Attachment>,
  onAttachmentTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  openError: String? = null,
  /** The records DATA_LOG references point at, keyed by data log id; null while not loaded. */
  dataLogs: Map<DataLogId, DataLogRowInfo>? = null,
) {
  if (attachments.isEmpty()) return

  Column(modifier = modifier) {
    FormSectionLabel(text = stringResource(Res.string.attachments))
    Spacer(Modifier.height(Spacing.small))
    attachments.forEach { attachment ->
      AttachmentRow(
        attachment = attachment,
        // A reference has no blob of its own; the row reads the DataLog's state from [dataLogs].
        syncState = if (attachment.type.isFile) syncStates[attachment.id] else null,
        onTap = onAttachmentTap,
        dataLogs = dataLogs,
      )
      HorizontalDivider()
    }
    if (openError != null) {
      Spacer(Modifier.height(Spacing.small))
      Text(
        text = openError.ifBlank { stringResource(Res.string.open_failed) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.statusColors.critical.accent,
      )
    }
  }
}
