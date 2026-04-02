package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.feature.aircraft.generated.resources.delete_inspection
import wingslog.feature.aircraft.generated.resources.delete_inspection_confirmation
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@Composable
fun DeleteInspectionConfirmDialog(
  inspectionTitle: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(cmpStringResource(AircraftRes.string.delete_inspection)) },
    text = {
      Text(
        cmpStringResource(AircraftRes.string.delete_inspection_confirmation, inspectionTitle)
      )
    },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(cmpStringResource(CoreRes.string.delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(cmpStringResource(CoreRes.string.cancel))
      }
    },
  )
}
