package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.tasks.sharedassets.generated.resources.Res
import wingslog.feature.tasks.sharedassets.generated.resources.skip_task_confirm_action
import wingslog.feature.tasks.sharedassets.generated.resources.skip_task_confirm_body
import wingslog.feature.tasks.sharedassets.generated.resources.skip_task_confirm_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
fun SkipTaskConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.skip_task_confirm_title)) },
    text = { Text(stringResource(Res.string.skip_task_confirm_body)) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(
          contentColor = MaterialTheme.statusColors.caution.accent
        ),
      ) {
        Text(stringResource(Res.string.skip_task_confirm_action))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
