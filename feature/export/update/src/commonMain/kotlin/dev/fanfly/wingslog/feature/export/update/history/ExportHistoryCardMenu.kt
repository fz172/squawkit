package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.popup.DropdownMenu
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_delete
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_download
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_resend
import wingslog.feature.export.sharedassets.generated.resources.export_history_menu_save

/** The overflow menu on an [ExportHistoryCard]: delivery, download and delete actions. */
@Composable
internal fun ExportHistoryCardMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  canResend: Boolean,
  canRetry: Boolean,
  canShareDevice: Boolean,
  canSaveToDevice: Boolean,
  hasUpperMenuItems: Boolean,
  onResendDelivery: () -> Unit,
  onRetryDelivery: () -> Unit,
  onDownload: () -> Unit,
  onSaveToDevice: () -> Unit,
  onDelete: () -> Unit,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismiss,
  ) {
    if (canResend || canRetry) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.export_history_menu_resend)) },
        leadingIcon = {
          Icon(
            Icons.Default.Email,
            contentDescription = null
          )
        },
        onClick = {
          onDismiss()
          if (canRetry) onRetryDelivery() else onResendDelivery()
        },
      )
    }
    if (canShareDevice) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.export_history_menu_download)) },
        leadingIcon = {
          Icon(
            Icons.Default.Download,
            contentDescription = null
          )
        },
        onClick = {
          onDismiss()
          onDownload()
        },
      )
    }
    if (canSaveToDevice) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.export_history_menu_save)) },
        leadingIcon = {
          Icon(
            Icons.Default.Download,
            contentDescription = null
          )
        },
        onClick = {
          onDismiss()
          onSaveToDevice()
        },
      )
    }
    if (hasUpperMenuItems) {
      HorizontalDivider(
        modifier = Modifier.padding(vertical = Spacing.extraSmall),
        color = MaterialTheme.colorScheme.outlineVariant,
      )
    }
    DropdownMenuItem(
      text = {
        Text(
          text = stringResource(Res.string.export_history_menu_delete),
          color = MaterialTheme.colorScheme.error,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
      },
      onClick = {
        onDismiss()
        onDelete()
      },
    )
  }
}
