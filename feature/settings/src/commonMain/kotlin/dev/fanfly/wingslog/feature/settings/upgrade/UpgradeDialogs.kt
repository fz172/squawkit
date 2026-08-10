package dev.fanfly.wingslog.feature.settings.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * The provider picker. [providers] comes from `upgradeProvidersFor`, so this renders whatever the
 * platform offers without knowing which platform it is on.
 */
@Composable
internal fun UpgradeProviderDialog(
  providers: List<AuthProvider>,
  onSelect: (AuthProvider) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Keep your records") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
          "Connect an account so your aircraft, logs, and records are backed up and " +
            "available on your other devices."
        )
        providers.forEach { provider ->
          TextButton(
            onClick = { onSelect(provider) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(provider.label(), modifier = Modifier.fillMaxWidth())
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
  )
}

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
    title = { Text("Continue with email") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text("We'll email you a link. Open it on this device to finish connecting your account.")
        OutlinedTextField(
          value = state.email,
          onValueChange = onEmailChange,
          singleLine = true,
          enabled = !state.sending,
          isError = state.error != null,
          label = { Text("Email address") },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
          ),
          modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it) }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onSend,
        enabled = !state.sending && state.email.isNotBlank(),
      ) {
        if (state.sending) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Text("Send link")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !state.sending) { Text("Cancel") }
    },
  )
}

/** Leg 1 is done and the app is waiting to be reopened by the link. */
@Composable
internal fun UpgradeLinkSentDialog(email: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Check your email") },
    text = {
      Text(
        "We sent a link to $email. Open it on this device and we'll ask you to confirm before " +
          "connecting your records."
      )
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}

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
    title = { Text("Connect this account?") },
    text = {
      Text(
        "Your records on this device will be connected to $email and backed up. " +
          "If that isn't your address, cancel and start again."
      )
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Connect") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

private fun AuthProvider.label(): String = when (this) {
  AuthProvider.Google -> "Continue with Google"
  AuthProvider.Apple -> "Continue with Apple"
  AuthProvider.Email -> "Continue with email"
}
