package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.upgrade_email_body
import wingslog.feature.login.generated.resources.upgrade_email_invalid
import wingslog.feature.login.generated.resources.upgrade_email_label
import wingslog.feature.login.generated.resources.upgrade_email_send
import wingslog.feature.login.generated.resources.upgrade_email_send_failed
import wingslog.feature.login.generated.resources.upgrade_email_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** Address entry for an email-link upgrade. Field state lives in the ViewModel, not here. */
@Composable
internal fun UpgradeEmailDialog(
  state: UpgradeUiState.EnteringEmail,
  onEmailChange: (String) -> Unit,
  onSend: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!state.sending) onDismiss() },
    title = { Text(stringResource(Res.string.upgrade_email_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(stringResource(Res.string.upgrade_email_body))
        OutlinedTextField(
          value = state.email,
          onValueChange = onEmailChange,
          singleLine = true,
          enabled = !state.sending,
          isError = state.error != null,
          label = { Text(stringResource(Res.string.upgrade_email_label)) },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
          ),
          modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { error ->
          Text(
            stringResource(
              when (error) {
                is EmailEntryError.InvalidAddress -> Res.string.upgrade_email_invalid
                is EmailEntryError.SendFailed -> Res.string.upgrade_email_send_failed
              }
            )
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onSend,
        enabled = !state.sending && state.email.isNotBlank(),
      ) {
        if (state.sending) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
          )
        } else {
          Text(stringResource(Res.string.upgrade_email_send))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !state.sending) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
