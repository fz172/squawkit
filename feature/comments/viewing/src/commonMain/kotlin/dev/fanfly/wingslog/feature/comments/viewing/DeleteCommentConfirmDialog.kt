package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_message
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * Deleting is the one comment action that cannot be undone — the tombstone is final by design —
 * and the item sits one row under "Update comment". Every other destructive action in the app
 * confirms; this one has more reason to than most.
 */
@Composable
internal fun DeleteCommentConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.comment_delete_title)) },
    text = { Text(stringResource(Res.string.comment_delete_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          text = stringResource(CoreRes.string.delete),
          color = MaterialTheme.colorScheme.error,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}
