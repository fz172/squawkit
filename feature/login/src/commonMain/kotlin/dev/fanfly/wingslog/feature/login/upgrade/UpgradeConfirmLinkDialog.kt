package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.upgrade_confirm_link_body
import wingslog.feature.login.generated.resources.upgrade_confirm_link_confirm
import wingslog.feature.login.generated.resources.upgrade_confirm_link_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The confirmation gate. Naming the address matters: it is what lets someone notice the link was
 * meant for a different account before anything is bound to this device's data.
 */
@Composable
internal fun UpgradeConfirmLinkDialog(
  email: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.upgrade_confirm_link_title)) },
    text = {
      Text(stringResource(Res.string.upgrade_confirm_link_body, email))
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(Res.string.upgrade_confirm_link_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}
