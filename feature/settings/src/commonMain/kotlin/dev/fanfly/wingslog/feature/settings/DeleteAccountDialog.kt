package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.data.AccountDeletion
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.settings.generated.resources.settings_delete_account_body
import wingslog.feature.settings.generated.resources.settings_delete_account_confirm
import wingslog.feature.settings.generated.resources.settings_delete_account_export_hint
import wingslog.feature.settings.generated.resources.settings_delete_account_failed
import wingslog.feature.settings.generated.resources.settings_delete_account_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.settings.generated.resources.Res as SettingsRes

/**
 * The confirmation in front of account deletion (#418).
 *
 * Names what actually goes — including the part a pilot cannot see from here, that **people they
 * have shared an aircraft with lose access to it**. Deletion tears the share down, so the cost
 * lands on someone who is not in the room; saying so is the difference between a confirmation and a
 * formality.
 *
 * The export hint is not decoration either: for anyone who wants a record, this is the last moment
 * it exists.
 */
@Composable
internal fun DeleteAccountDialog(
  state: AccountDeletion,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  if (state == AccountDeletion.Idle) return
  val working = state == AccountDeletion.Working

  AlertDialog(
    // Not dismissable while the server is partway through deleting things.
    onDismissRequest = { if (!working) onDismiss() },
    title = { Text(stringResource(SettingsRes.string.settings_delete_account_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(SettingsRes.string.settings_delete_account_body))
        Text(
          text = stringResource(SettingsRes.string.settings_delete_account_export_hint),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state == AccountDeletion.Failed) {
          // "Nothing has been removed" is the load-bearing half: the account and its data are
          // intact, so retrying is safe and the pilot has not silently lost anything.
          Text(
            text = stringResource(SettingsRes.string.settings_delete_account_failed),
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm, enabled = !working) {
        if (working) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
          )
        } else {
          Text(
            text = stringResource(SettingsRes.string.settings_delete_account_confirm),
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !working) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
