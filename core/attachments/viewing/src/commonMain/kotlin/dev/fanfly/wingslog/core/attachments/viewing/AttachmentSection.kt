package dev.fanfly.wingslog.core.attachments.viewing

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
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.attachments.sharedassets.generated.resources.Res
import wingslog.core.attachments.sharedassets.generated.resources.attachments

/**
 * Read-only list of attachments shown on detail views.
 * Hidden when [attachments] is empty (per PRD F3/F4).
 */
@Composable
fun AttachmentSection(
  attachments: List<Attachment>,
  onAttachmentTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
  downloadingIds: Set<String> = emptySet(),
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
        onTap = onAttachmentTap,
        isDownloading = downloadingIds.contains(attachment.id)
      )
      HorizontalDivider()
    }
  }
}
