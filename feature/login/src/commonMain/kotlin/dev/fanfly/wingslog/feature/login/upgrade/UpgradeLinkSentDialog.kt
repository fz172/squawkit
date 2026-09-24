package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.done
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.upgrade_link_sent_body
import wingslog.feature.login.generated.resources.upgrade_link_sent_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** Leg 1 is done and the app is waiting to be reopened by the link. */
@Composable
internal fun UpgradeLinkSentDialog(email: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.upgrade_link_sent_title)) },
    text = {
      Text(stringResource(Res.string.upgrade_link_sent_body, email))
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.done)) }
    },
  )
}
