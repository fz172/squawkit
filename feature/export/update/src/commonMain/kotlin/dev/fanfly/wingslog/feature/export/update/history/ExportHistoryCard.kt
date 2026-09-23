package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.export.ExportRecord
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_delete_confirm_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_item_meta
import wingslog.feature.export.sharedassets.generated.resources.export_history_more_actions
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun ExportHistoryCard(
  record: ExportRecord,
  canEmailDelivery: Boolean,
  onDownloadExport: (exportId: String, filePath: String, fileName: String) -> Unit,
  onResendDelivery: () -> Unit,
  onRetryDelivery: () -> Unit,
  onSaveToDevice: () -> Unit,
  onDelete: () -> Unit,
) {
  var showDeleteConfirm by remember { mutableStateOf(false) }
  var menuExpanded by remember { mutableStateOf(false) }
  val thingTitle = thingSummary(record)
  val scope = scopeLine(record)
  val onDevice = record.file_path.isNotBlank()
  val canRetry =
    record.persisted_delivery_state == "FAILED" &&
      record.remote_archive_ref.isNotBlank() &&
      record.destination_email.isNotBlank()
  // Email-account users re-send the export by email from the remote archive (no local file needed).
  // The retry affordance already covers the failed case, so don't double up.
  val canResend = canEmailDelivery &&
    record.remote_archive_ref.isNotBlank() &&
    record.destination_email.isNotBlank() &&
    !canRetry
  // Any on-device file can go through the native share sheet, including one an email user pulled
  // down via "Save to device".
  val canShareDevice = onDevice
  // Remote-only archives can be pulled down to the device for offline sharing.
  val canSaveToDevice = !onDevice && record.remote_archive_ref.isNotBlank()
  val hasUpperMenuItems =
    canResend || canRetry || canShareDevice || canSaveToDevice

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(RoundedCornerShape(Spacing.cardCornerRadius))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.FolderZip,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = thingTitle,
        style = if (record.aircraft.isNotEmpty()) WingslogTypography.dataMedium else MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (scope.isNotBlank()) {
        Text(
          text = scope,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      val storageStatus = exportStorageStatus(
        canEmailDelivery = canEmailDelivery,
        onDevice = onDevice,
        onRemote = record.remote_archive_ref.isNotBlank(),
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Text(
          text = stringResource(
            Res.string.export_history_item_meta,
            formatDate(record.created_at_epoch_millis),
            record.size_bytes.formatFileSize(),
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (storageStatus != null) {
          Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Icon(
            imageVector = storageStatus.icon,
            contentDescription = null,
            tint = storageStatus.color,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = stringResource(storageStatus.label),
            style = MaterialTheme.typography.bodySmall,
            color = storageStatus.color,
          )
        }
      }
    }

    Box {
      IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = Icons.Default.MoreVert,
          contentDescription = stringResource(Res.string.export_history_more_actions),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      ExportHistoryCardMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        canResend = canResend,
        canRetry = canRetry,
        canShareDevice = canShareDevice,
        canSaveToDevice = canSaveToDevice,
        hasUpperMenuItems = hasUpperMenuItems,
        onResendDelivery = onResendDelivery,
        onRetryDelivery = onRetryDelivery,
        onDownload = { onDownloadExport(record.export_id, record.file_path, record.file_name) },
        onSaveToDevice = onSaveToDevice,
        onDelete = { showDeleteConfirm = true },
      )
    }
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text(stringResource(Res.string.export_history_delete_confirm_title)) },
      text = {
        Text(deleteConfirmBody(record))
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirm = false
            onDelete()
          },
        ) {
          Text(stringResource(CoreRes.string.delete).uppercase())
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text(stringResource(CoreRes.string.cancel).uppercase())
        }
      },
    )
  }
}
