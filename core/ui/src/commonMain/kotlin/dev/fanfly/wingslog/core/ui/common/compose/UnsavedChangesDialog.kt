package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.discard
import wingslog.core.ui.generated.resources.unsaved_changes
import wingslog.core.ui.generated.resources.unsaved_changes_message

@Composable
fun UnsavedChangesDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.unsaved_changes)) },
    text = { Text(stringResource(Res.string.unsaved_changes_message)) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(stringResource(Res.string.discard))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(Res.string.cancel))
      }
    },
  )
}
