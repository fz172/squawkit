package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.data.AccountDeletion
import dev.fanfly.wingslog.feature.settings.data.DeletionChallenge
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.settings.generated.resources.settings_delete_account_body
import wingslog.feature.settings.generated.resources.settings_delete_account_challenge_email
import wingslog.feature.settings.generated.resources.settings_delete_account_challenge_hint
import wingslog.feature.settings.generated.resources.settings_delete_account_challenge_mismatch
import wingslog.feature.settings.generated.resources.settings_delete_account_challenge_phrase
import wingslog.feature.settings.generated.resources.settings_delete_account_challenge_word
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
 *
 * And a button is not enough of a gate on its own. Deleting is irreversible and takes everyone
 * else's access with it, so the pilot has to type the [DeletionChallenge] out — their own email
 * address, or a fixed phrase when we have no address they would recognise. The comparison lives
 * here rather than in the ViewModel because the fallback phrase is a localized string.
 */
@Composable
internal fun DeleteAccountDialog(
  state: AccountDeletion,
  challenge: DeletionChallenge,
  typed: String,
  onTypedChange: (String) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  if (state == AccountDeletion.Idle) return
  val working = state == AccountDeletion.Working

  val required = when (challenge) {
    is DeletionChallenge.Email -> challenge.address
    DeletionChallenge.Phrase ->
      stringResource(SettingsRes.string.settings_delete_account_challenge_word)
  }
  // Case-insensitive and trimmed: an autocapitalised first letter or a trailing space from a paste
  // is a typing artefact, not a sign they meant something else.
  val matches = typed.trim().equals(required, ignoreCase = true)

  AlertDialog(
    // Not dismissable while the server is partway through deleting things.
    onDismissRequest = { if (!working) onDismiss() },
    title = { Text(stringResource(SettingsRes.string.settings_delete_account_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Text(stringResource(SettingsRes.string.settings_delete_account_body))
        Text(
          text = stringResource(SettingsRes.string.settings_delete_account_export_hint),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = when (challenge) {
            is DeletionChallenge.Email ->
              stringResource(SettingsRes.string.settings_delete_account_challenge_email)
            DeletionChallenge.Phrase ->
              stringResource(SettingsRes.string.settings_delete_account_challenge_phrase)
          }
        )
        // The target, spelled out. Reading it off the dialog is the point — nobody should have to
        // go hunting for which address this account signs in with.
        Text(text = required, fontWeight = FontWeight.Bold)
        OutlinedTextField(
          value = typed,
          onValueChange = onTypedChange,
          modifier = Modifier.fillMaxWidth(),
          enabled = !working,
          singleLine = true,
          isError = typed.isNotBlank() && !matches,
          label = { Text(stringResource(SettingsRes.string.settings_delete_account_challenge_hint)) },
          keyboardOptions = KeyboardOptions(
            keyboardType = when (challenge) {
              is DeletionChallenge.Email -> KeyboardType.Email
              DeletionChallenge.Phrase -> KeyboardType.Text
            },
            imeAction = ImeAction.Done,
          ),
        )
        // Only once they have typed something. An empty field is not a mistake, it is the start.
        if (typed.isNotBlank() && !matches) {
          Text(
            text = stringResource(SettingsRes.string.settings_delete_account_challenge_mismatch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
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
      TextButton(onClick = onConfirm, enabled = matches && !working) {
        if (working) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
          )
        } else {
          Text(
            text = stringResource(SettingsRes.string.settings_delete_account_confirm),
            color = if (matches) {
              MaterialTheme.colorScheme.error
            } else {
              // Destructive red on a button that cannot fire reads as a dare. Muted until it can.
              MaterialTheme.colorScheme.onSurfaceVariant
            },
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
