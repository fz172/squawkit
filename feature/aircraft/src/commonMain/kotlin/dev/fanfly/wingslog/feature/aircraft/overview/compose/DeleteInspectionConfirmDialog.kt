package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import org.jetbrains.compose.resources.StringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.generated.resources.*
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.core.ui.generated.resources.*

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
        Text(cmpStringResource(AircraftRes.string.delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(cmpStringResource(AircraftRes.string.cancel))
      }
    },
  )
}
