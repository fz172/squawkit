package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.remove
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes
import wingslog.feature.attachment.sharedassets.generated.resources.add_attachment
import wingslog.feature.attachment.sharedassets.generated.resources.add_link
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_limits_hint
import wingslog.feature.attachment.sharedassets.generated.resources.attachments
import wingslog.feature.attachment.sharedassets.generated.resources.choose_file
import wingslog.feature.attachment.sharedassets.generated.resources.delete_saved_attachment_message
import wingslog.feature.attachment.sharedassets.generated.resources.delete_saved_attachment_title
import wingslog.feature.attachment.sharedassets.generated.resources.invalid_url
import wingslog.feature.attachment.sharedassets.generated.resources.link_name
import wingslog.feature.attachment.sharedassets.generated.resources.link_url
import wingslog.feature.attachment.sharedassets.generated.resources.max_files_reached
import wingslog.feature.attachment.sharedassets.generated.resources.no_attachments
import wingslog.feature.attachment.sharedassets.generated.resources.remove_attachment
import wingslog.feature.attachment.sharedassets.generated.resources.sign_in_to_add_attachments

/**
 * Attachment section for forms. R2 simplification: every locally-added attachment is already on
 * disk by the time it lands in the list, so there are no Uploading / Failed UI variants — only
 * Local (a sha256-populated proto), Saved, LocalLink, and PendingDelete. Per-attachment upload
 * status is rendered separately if needed via `AttachmentManager.observeStatus`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentFormSection(
  visibleAttachments: List<PendingAttachment>,
  isAnonymous: Boolean,
  filesAtLimit: Boolean,
  showPickerSheet: Boolean,
  onAddClick: () -> Unit,
  onRemove: (String) -> Unit,
  onPickFiles: (List<PickedFile>) -> Unit,
  onAddLink: (url: String, name: String) -> Unit,
  onDismissSheet: () -> Unit,
  modifier: Modifier = Modifier,
  onPickError: () -> Unit = {},
  uploadEnabled: Boolean = true,
) {
  val pickFiles = rememberFilePicker(
    onResult = { files -> onPickFiles(files) },
    onReadError = onPickError,
  )

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(AttachRes.string.attachments),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (uploadEnabled) {
        if (!isAnonymous) {
          OutlinedButton(
            onClick = onAddClick,
            contentPadding = PaddingValues(
              horizontal = Spacing.medium,
              vertical = Spacing.extraSmall
            ),
          ) {
            Icon(
              Icons.Default.Add,
              contentDescription = null,
              modifier = Modifier.size(Spacing.large)
            )
            Spacer(Modifier.width(Spacing.extraSmall))
            Text(
              stringResource(AttachRes.string.add_attachment),
              style = MaterialTheme.typography.labelMedium,
            )
          }
        } else {
          Text(
            text = stringResource(AttachRes.string.sign_in_to_add_attachments),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (visibleAttachments.isEmpty()) {
      Text(
        text = stringResource(AttachRes.string.no_attachments),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      visibleAttachments.forEach { pending ->
        PendingAttachmentRow(
          pending = pending,
          onRemove = { onRemove(pending.id) })
        HorizontalDivider()
      }
    }
  }

  if (showPickerSheet) {
    AttachmentPickerSheet(
      filesAtLimit = filesAtLimit,
      onChooseFile = { onDismissSheet(); pickFiles() },
      onAddLink = { url, name ->
        onAddLink(
          url,
          name
        ); onDismissSheet()
      },
      onDismiss = onDismissSheet,
    )
  }
}

@Composable
private fun PendingAttachmentRow(
  pending: PendingAttachment,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isSavedFile = pending is PendingAttachment.Saved &&
    pending.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK
  var showConfirmDialog by remember { mutableStateOf(false) }

  if (showConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showConfirmDialog = false },
      title = { Text(stringResource(AttachRes.string.delete_saved_attachment_title)) },
      text = { Text(stringResource(AttachRes.string.delete_saved_attachment_message)) },
      confirmButton = {
        TextButton(onClick = { onRemove(); showConfirmDialog = false }) {
          Text(stringResource(CoreRes.string.remove))
        }
      },
      dismissButton = {
        TextButton(onClick = { showConfirmDialog = false }) {
          Text(stringResource(CoreRes.string.cancel))
        }
      },
    )
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        imageVector = pending.typeIcon(),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = pending.name,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = { if (isSavedFile) showConfirmDialog = true else onRemove() }) {
        Icon(
          Icons.Default.Close,
          contentDescription = stringResource(AttachRes.string.remove_attachment),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Spacer(Modifier.fillMaxWidth().height(6.dp))
  }
}

private fun PendingAttachment.typeIcon() = when (this) {
  is PendingAttachment.LocalLink -> Icons.Outlined.Link
  is PendingAttachment.Saved -> attachment.type.toIcon()
  is PendingAttachment.Local -> attachment.type.toIcon()
  is PendingAttachment.PendingDelete -> attachment.type.toIcon()
}

private fun AttachmentType.toIcon() = when (this) {
  AttachmentType.ATTACHMENT_TYPE_PDF -> Icons.Outlined.PictureAsPdf
  AttachmentType.ATTACHMENT_TYPE_IMAGE -> Icons.Outlined.Image
  AttachmentType.ATTACHMENT_TYPE_LINK -> Icons.Outlined.Link
  else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentPickerSheet(
  filesAtLimit: Boolean,
  onChooseFile: () -> Unit,
  onAddLink: (url: String, name: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showLinkField by remember { mutableStateOf(false) }
  var linkUrl by remember { mutableStateOf("") }
  var linkName by remember { mutableStateOf("") }
  var urlError by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      if (!showLinkField) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          OutlinedButton(
            onClick = onChooseFile,
            enabled = !filesAtLimit,
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              Icons.Default.Add,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.small))
            Text(stringResource(AttachRes.string.choose_file))
          }
          OutlinedButton(
            onClick = { showLinkField = true },
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              Icons.Outlined.Link,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.small))
            Text(stringResource(AttachRes.string.add_link))
          }
        }
        Text(
          text = if (filesAtLimit) {
            stringResource(AttachRes.string.max_files_reached)
          } else {
            stringResource(AttachRes.string.attachment_limits_hint)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        OutlinedTextField(
          value = linkUrl,
          onValueChange = { linkUrl = it; urlError = false },
          label = { Text(stringResource(AttachRes.string.link_url)) },
          isError = urlError,
          supportingText = if (urlError) {
            { Text(stringResource(AttachRes.string.invalid_url)) }
          } else null,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        OutlinedTextField(
          value = linkName,
          onValueChange = { linkName = it },
          label = { Text(stringResource(AttachRes.string.link_name)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier.fillMaxWidth()
        ) {
          TextButton(onClick = { showLinkField = false; linkUrl = ""; linkName = "" }) {
            Text(stringResource(CoreRes.string.cancel))
          }
          FilledTonalButton(onClick = {
            val trimmed = linkUrl.trim()
            if (!isValidUrl(trimmed)) {
              urlError = true
            } else {
              val normalized =
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                  trimmed
                } else {
                  "https://$trimmed"
                }
              val finalName = linkName.trim().ifBlank { normalized.extractDomain() }
              onAddLink(
                normalized,
                finalName
              )
            }
          }) {
            Text(stringResource(AttachRes.string.add_link))
          }
        }
      }
      Spacer(Modifier.height(Spacing.huge))
    }
  }
}

private fun String.extractDomain(): String {
  val withoutScheme = if (contains("://")) substringAfter("://") else this
  val hostAndPort = withoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
  val host = hostAndPort.substringBefore(":")
  return if (host.startsWith("www.")) host.drop(4) else host
}

private fun isValidUrl(url: String): Boolean {
  if (url.isBlank()) return false
  val lower = url.lowercase().trim()
  if (lower.startsWith("ftp://") || lower.startsWith("file://") || lower.startsWith("mailto:")) return false
  val normalized =
    if (lower.startsWith("http://") || lower.startsWith("https://")) lower else "https://$lower"
  val withoutProtocol = normalized.substringAfter("://")
  val dotIndex = withoutProtocol.indexOf('.')
  return dotIndex > 0 && dotIndex < withoutProtocol.lastIndex && !withoutProtocol.contains(' ')
}
