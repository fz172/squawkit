package dev.fanfly.wingslog.feature.technician.manage.edit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.save
import wingslog.feature.technician.sharedassets.generated.resources.name_required
import wingslog.feature.technician.sharedassets.generated.resources.technician_update_name
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

/** The name editor: one field in a dialog, so the Details card stays a record rather than a form. */
@Composable
internal fun RenameDialog(
  draft: String,
  onDraftChange: (String) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(TechnicianRes.string.technician_update_name)) },
    text = {
      FormTextField(
        value = draft,
        onValueChange = onDraftChange,
        label = stringResource(TechnicianRes.string.name_required),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = onConfirm, enabled = draft.isNotBlank()) {
        Text(stringResource(CoreRes.string.save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}
