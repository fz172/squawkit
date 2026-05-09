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
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState

@Composable
fun AttachmentRow(
  attachment: Attachment,
  syncState: BlobSyncState? = null,
  onTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
) {
  val enabled = when (syncState) {
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
        text = attachment.subtitle(),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    when (syncState) {
      BlobSyncState.Uploading,
      BlobSyncState.Downloading,
        -> CircularProgressIndicator(
        modifier = Modifier.size(Spacing.large),
        strokeWidth = Spacing.tiny,
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
        tint = MaterialTheme.colorScheme.error,
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

private fun Attachment.subtitle(): String = when (type) {
  AttachmentType.ATTACHMENT_TYPE_LINK -> url.displayDomain()
  else -> buildString {
    if (mime_type.isNotBlank()) append(mime_type.mimeLabel())
    if (size_bytes > 0L) {
      if (isNotEmpty()) append(" · ")
      append(size_bytes.formatFileSize())
    }
  }
}

private fun String.displayDomain(): String {
  val host = this.substringAfter("://").substringBefore("/")
  return if (host.length > 40) host.take(37) + "…" else host
}

private fun String.mimeLabel(): String = when {
  startsWith("image/") -> "Image"
  this == "application/pdf" -> "PDF"
  startsWith("text/") -> "Text"
  else -> substringAfterLast("/").uppercase().take(8)
}

private fun Long.formatFileSize(): String = when {
  this >= 1_048_576L -> {
    val mb = this / 1_048_576.0
    val rounded = (mb * 10).toInt() / 10.0
    "$rounded MB"
  }

  this >= 1_024L -> "${this / 1024} KB"
  else -> "$this B"
}
