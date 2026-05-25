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
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.attachment.sharedassets.generated.resources.Res
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_type_image
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_type_pdf
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_type_text
import wingslog.feature.attachment.sharedassets.generated.resources.file_size_bytes
import wingslog.feature.attachment.sharedassets.generated.resources.file_size_kb
import wingslog.feature.attachment.sharedassets.generated.resources.file_size_mb
import wingslog.feature.attachment.sharedassets.generated.resources.subtitle_separator

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
    if (mime_type.isNotBlank()) append(mime_type.mimeLabel())
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
private fun String.mimeLabel(): String = when {
  startsWith("image/") -> stringResource(Res.string.attachment_type_image)
  this == "application/pdf" -> stringResource(Res.string.attachment_type_pdf)
  startsWith("text/") -> stringResource(Res.string.attachment_type_text)
  else -> substringAfterLast("/").uppercase()
    .take(8)
}

@Composable
private fun Long.formatFileSize(): String = when {
  this >= 1_048_576L -> {
    val mb = this / 1_048_576.0
    val rounded = (mb * 10).toInt() / 10.0
    stringResource(Res.string.file_size_mb, rounded.toString())
  }

  this >= 1_024L -> stringResource(
    Res.string.file_size_kb,
    (this / 1024).toString()
  )

  else -> stringResource(Res.string.file_size_bytes, toString())
}
