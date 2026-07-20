package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.attachment.sharedassets.generated.resources.Res
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_not_shared
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_type_image
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_type_pdf
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_type_text
import wingslog.feature.attachment.sharedassets.generated.resources.subtitle_separator

@Composable
fun AttachmentRow(
  attachment: Attachment,
  syncState: BlobSyncState? = null,
  onTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
  /**
   * The file lives in another account's storage and v1 does not share blobs (design §9), so it can
   * never arrive on this device. The row stays visible — the attachment genuinely exists on the log —
   * but it is inert and says so, rather than offering a tap that silently does nothing.
   */
  unavailable: Boolean = false,
) {
  val enabled = !unavailable && when (syncState) {
    null,
    BlobSyncState.Synced,
    BlobSyncState.RemoteOnly,
    BlobSyncState.UploadFailed,
      -> true

    BlobSyncState.PendingUpload,
    BlobSyncState.Uploading,
    BlobSyncState.Downloading,
      -> false
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(enabled = enabled) { onTap(attachment) }
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = attachment.typeIcon(),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(Spacing.extraLarge),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = attachment.name,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = if (unavailable) stringResource(Res.string.attachment_not_shared)
        else attachment.subtitle(),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (unavailable) {
      Icon(
        imageVector = Icons.Filled.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.size(Spacing.large),
      )
      return@Row
    }
    when (syncState) {
      BlobSyncState.Uploading,
      BlobSyncState.Downloading,
        -> CircularProgressIndicator(
        modifier = Modifier.size(Spacing.large),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      BlobSyncState.PendingUpload -> Icon(
        imageVector = Icons.Filled.CloudUpload,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.size(Spacing.large),
      )

      BlobSyncState.UploadFailed -> Icon(
        imageVector = Icons.Filled.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.statusColors.critical.accent,
        modifier = Modifier.size(Spacing.large),
      )

      BlobSyncState.RemoteOnly -> Icon(
        imageVector = Icons.Filled.CloudDownload,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.size(Spacing.large),
      )

      null,
      BlobSyncState.Synced,
        -> Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.size(Spacing.large),
      )
    }
  }
}

private fun Attachment.typeIcon() = when (type) {
  AttachmentType.ATTACHMENT_TYPE_PDF -> Icons.Outlined.PictureAsPdf
  AttachmentType.ATTACHMENT_TYPE_LINK -> Icons.Outlined.Link
  AttachmentType.ATTACHMENT_TYPE_IMAGE -> Icons.Outlined.Image
  else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

@Composable
private fun Attachment.subtitle(): String = when (type) {
  AttachmentType.ATTACHMENT_TYPE_LINK -> url.displayDomain()
  else -> buildString {
    val label = typeLabel()
    if (label.isNotBlank()) append(label)
    if (size_bytes > 0L) {
      if (isNotEmpty()) append(stringResource(Res.string.subtitle_separator))
      append(size_bytes.formatFileSize())
    }
  }
}

private fun String.displayDomain(): String {
  val host = this.substringAfter("://")
    .substringBefore("/")
  return if (host.length > 40) host.take(37) + "…" else host
}

@Composable
private fun Attachment.typeLabel(): String = when {
  mime_type.startsWith("image/") -> stringResource(Res.string.attachment_type_image)
  mime_type == "application/pdf" -> stringResource(Res.string.attachment_type_pdf)
  mime_type.startsWith("text/") -> stringResource(Res.string.attachment_type_text)
  else -> {
    // Vendor mime subtypes are long and unfriendly — e.g. .docx is
    // "vnd.openxmlformats-officedocument.wordprocessingml.document", which truncates to "VND.OPEN".
    // Prefer the filename extension (DOCX, XLSX, ZIP…), which is what users recognize.
    val ext = name.substringAfterLast('.', "")
    when {
      ext.length in 1..5 -> ext.uppercase()
      mime_type.isNotBlank() -> mime_type.substringAfterLast('/')
        .uppercase()
        .take(8)

      else -> ""
    }
  }
}
