package dev.fanfly.wingslog.core.attachments.viewing

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography

@Composable
fun AttachmentRow(
  attachment: Attachment,
  isDownloading: Boolean = false,
  onTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(enabled = !isDownloading) { onTap(attachment) }
      .padding(vertical = Spacing.small)
      .alpha(if (isDownloading) 0.5f else 1.0f),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = attachment.typeIcon(),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp),
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
    if (isDownloading) {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.size(16.dp),
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
