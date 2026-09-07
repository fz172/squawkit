package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.logs.sharedassets.generated.resources.Res
import wingslog.feature.logs.sharedassets.generated.resources.delete_log
import wingslog.feature.logs.sharedassets.generated.resources.this_action_cannot_be_undone
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
fun DeleteLogConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.delete_log)) },
    text = { Text(stringResource(Res.string.this_action_cannot_be_undone)) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(stringResource(CoreRes.string.delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
