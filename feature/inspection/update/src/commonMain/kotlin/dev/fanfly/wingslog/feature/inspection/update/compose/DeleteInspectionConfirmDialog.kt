package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.feature.inspection.update.generated.resources.delete_task
import wingslog.feature.inspection.update.generated.resources.delete_task_confirmation
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes

@Composable
fun DeleteInspectionConfirmDialog(
  inspectionTitle: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(InspectionRes.string.delete_task)) },
    text = {
      Text(
        stringResource(InspectionRes.string.delete_task_confirmation, inspectionTitle)
      )
    },
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
