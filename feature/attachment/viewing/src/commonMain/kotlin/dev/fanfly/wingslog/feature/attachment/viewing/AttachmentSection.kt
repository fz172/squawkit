package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
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
) {
  if (attachments.isEmpty()) return

  Column(modifier = modifier) {
    Text(
      text = stringResource(Res.string.attachments),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(Spacing.small))
    attachments.forEach { attachment ->
      AttachmentRow(
        attachment = attachment,
        syncState = if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) null
                    else syncStates[attachment.id],
        onTap = onAttachmentTap,
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
