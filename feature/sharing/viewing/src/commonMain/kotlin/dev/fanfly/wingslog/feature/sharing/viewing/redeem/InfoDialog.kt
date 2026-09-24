package dev.fanfly.wingslog.feature.sharing.viewing.redeem

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(
          stringResource(
            CoreRes.string.ok
          )
        )
      }
    },
  )
}
