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
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.fileCount
import dev.fanfly.wingslog.feature.attachment.model.isFile
import dev.fanfly.wingslog.feature.subscription.viewing.ProUpsellSheet
import dev.fanfly.wingslog.feature.subscription.viewing.UpsellTrigger
import dev.fanfly.wingslog.thing.AttachmentType
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.attachment.sharedassets.generated.resources.attachments
import wingslog.feature.attachment.sharedassets.generated.resources.delete_saved_attachment_message
import wingslog.feature.attachment.sharedassets.generated.resources.delete_saved_attachment_title
import wingslog.feature.attachment.sharedassets.generated.resources.no_attachments
import wingslog.feature.attachment.sharedassets.generated.resources.remove_attachment
import wingslog.feature.attachment.sharedassets.generated.resources.sign_in_to_add_attachments
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

/**
 * Attachment section for forms. R2 simplification: every locally-added attachment is already on
 * disk by the time it lands in the list, so there are no Uploading / Failed UI variants — only
 * Local (a sha256-populated proto), Saved, LocalLink, and PendingDelete. Per-attachment upload
 * status is rendered separately if needed via `AttachmentManager.observeStatus`.
 */
@Composable
fun AttachmentFormSection(
  visibleAttachments: List<PendingAttachment>,
  isAnonymous: Boolean,
  filesAtLimit: Boolean,
  // Gates only the file/photo picker options (the subscription's canUploadAttachments). Links are
  // always available; when off, the upload options become a locked promo that opens [ProUpsellSheet].
  uploadEnabled: Boolean,
  showPickerSheet: Boolean,
  onAddClick: () -> Unit,
  onRemove: (String) -> Unit,
  onPickFiles: (List<PickedFile>) -> Unit,
  onAddLink: (url: String, name: String) -> Unit,
  onDismissSheet: () -> Unit,
  modifier: Modifier = Modifier,
  onPickError: () -> Unit = {},
  // When attachments are gated off, tapping a locked upload option routes here (navigate to the
  // subscription page). Null keeps the pre-subscription behavior (options simply disabled).
  onSeePlans: (() -> Unit)? = null,
  // Null hides the data log option: the Thing has no data logs section or this build has no
  // visualizer (design §9.2). The form screen supplies the body because this module cannot
  // depend on feature/datalog.
  dataLogPicker: DataLogPickerSlot? = null,
  onAttachDataLog: (DataLogId, String) -> Unit = { _, _ -> },
) {
  var showUpsell by remember { mutableStateOf(false) }
  val pickFiles = rememberFilePicker(
    onResult = { files -> onPickFiles(files) },
    onReadError = onPickError,
  )
  val takePhoto = rememberCameraCapture(
    onResult = { files -> onPickFiles(files) },
    onError = onPickError,
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
      FormSectionLabel(text = stringResource(AttachRes.string.attachments))
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
            stringResource(CoreRes.string.add),
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
      }
    }
  }

  if (showPickerSheet) {
    AttachmentPicker(
      fileCount = visibleAttachments.fileCount(),
      filesAtLimit = filesAtLimit,
      uploadEnabled = uploadEnabled,
      onChooseFile = { onDismissSheet(); pickFiles() },
      onTakePhoto = { onDismissSheet(); takePhoto() },
      onAddLink = { url, name ->
        onAddLink(
          url,
          name
        ); onDismissSheet()
      },
      // Locked upload option tapped: close the picker, then surface the promo (avoids a nested sheet).
      onUpsell = onSeePlans?.let { { onDismissSheet(); showUpsell = true } },
      onDismiss = onDismissSheet,
      dataLogPicker = dataLogPicker,
      onAttachDataLog = { id, name -> onAttachDataLog(id, name); onDismissSheet() },
    )
  }

  if (showUpsell && onSeePlans != null) {
    ProUpsellSheet(
      trigger = UpsellTrigger.ATTACHMENT_UPLOAD,
      onSeePlans = { onSeePlans(); showUpsell = false },
      onDismiss = { showUpsell = false },
    )
  }
}

@Composable
private fun PendingAttachmentRow(
  pending: PendingAttachment,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Only a saved file needs the warning: links and data log references own no bytes.
  val isSavedFile =
    pending is PendingAttachment.Saved && pending.attachment.type.isFile
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
        modifier = Modifier.size(Spacing.xLarge),
      )
      Text(
        text = pending.name,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = {
        if (isSavedFile) showConfirmDialog = true else onRemove()
      }) {
        Icon(
          Icons.Default.Close,
          contentDescription = stringResource(AttachRes.string.remove_attachment),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(Spacing.large),
        )
      }
    }
    Spacer(
      Modifier.fillMaxWidth()
        .height(Spacing.extraSmall)
    )
  }
}

private fun PendingAttachment.typeIcon() = when (this) {
  is PendingAttachment.LocalLink -> Icons.Outlined.Link
  is PendingAttachment.LocalDataLogRef -> Icons.Outlined.ShowChart
  is PendingAttachment.Saved -> attachment.type.toIcon()
  is PendingAttachment.Local -> attachment.type.toIcon()
  is PendingAttachment.PendingDelete -> attachment.type.toIcon()
}

private fun AttachmentType.toIcon() = when (this) {
  AttachmentType.ATTACHMENT_TYPE_PDF -> Icons.Outlined.PictureAsPdf
  AttachmentType.ATTACHMENT_TYPE_IMAGE -> Icons.Outlined.Image
  AttachmentType.ATTACHMENT_TYPE_LINK -> Icons.Outlined.Link
  AttachmentType.ATTACHMENT_TYPE_DATA_LOG -> Icons.Outlined.ShowChart
  else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
