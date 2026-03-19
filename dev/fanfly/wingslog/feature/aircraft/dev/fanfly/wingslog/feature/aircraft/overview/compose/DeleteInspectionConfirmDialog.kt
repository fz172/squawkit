package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.fanfly.wingslog.feature.aircraft.R

@Composable
fun DeleteInspectionConfirmDialog(
  inspectionTitle: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.delete_inspection)) },
    text = {
      Text(
        stringResource(R.string.delete_inspection_confirmation, inspectionTitle)
      )
    },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(stringResource(R.string.delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
