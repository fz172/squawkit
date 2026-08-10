package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.AppleButtonBackground
import dev.fanfly.wingslog.feature.login.AppleButtonContent
import dev.fanfly.wingslog.feature.login.LoginButtonLabelStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.ic_google_rd_na
import wingslog.feature.login.generated.resources.sign_in_with_apple
import wingslog.feature.login.generated.resources.sign_in_with_email
import wingslog.feature.login.generated.resources.sign_in_with_google

/** Matches the login page's buttons: full width, 54dp, rounded, icon + label. */
private val ProviderButtonHeight = 54.dp

/**
 * The provider picker, as a bottom sheet. [providers] comes from `upgradeProvidersFor`, so this
 * renders whatever the platform offers without knowing which platform it is on.
 *
 * The buttons reuse `LoginCommon`'s colours, label style, icons and strings directly — living in
 * feature/login is what makes that possible, and it is why the sheet cannot drift away from the
 * full-screen login page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeProviderSheet(
  providers: List<AuthProvider>,
  onSelect: (AuthProvider) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.large)
        .padding(bottom = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = "Keep your records",
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = "Connect an account so your aircraft, logs, and records are backed up and " +
          "available on your other devices.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(Spacing.small))

      providers.forEach { provider ->
        when (provider) {
          AuthProvider.Apple -> ProviderButton(
            container = AppleButtonBackground,
            content = AppleButtonContent,
            icon = { tint ->
              Icon(
                painter = painterResource(Res.drawable.ic_apple),
                contentDescription = stringResource(Res.string.apple_logo),
                modifier = Modifier.size(Spacing.xLarge),
                tint = tint,
              )
            },
            label = stringResource(Res.string.sign_in_with_apple),
            onClick = { onSelect(AuthProvider.Apple) },
          )

          // The login page's Google button is near-white with the multi-colour mark, which must not
          // be tinted — so the icon slot takes the tint rather than assuming it.
          AuthProvider.Google -> ProviderButton(
            container = MaterialTheme.colorScheme.surface,
            content = MaterialTheme.colorScheme.onSurface,
            icon = {
              Icon(
                painter = painterResource(Res.drawable.ic_google_rd_na),
                contentDescription = stringResource(Res.string.google_logo),
                modifier = Modifier.size(Spacing.xLarge),
                tint = Color.Unspecified,
              )
            },
            label = stringResource(Res.string.sign_in_with_google),
            onClick = { onSelect(AuthProvider.Google) },
          )

          // Email is deliberately the quieter option: it opens an address form rather than signing
          // in, so giving it a branded provider button would overstate what tapping it does.
          AuthProvider.Email -> OutlinedButton(
            modifier = Modifier
              .fillMaxWidth()
              .height(ProviderButtonHeight),
            shape = RoundedCornerShape(Spacing.buttonCornerRadius),
            onClick = { onSelect(AuthProvider.Email) },
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(Spacing.xLarge),
              )
              Text(
                text = stringResource(Res.string.sign_in_with_email),
                style = LoginButtonLabelStyle,
              )
            }
          }
        }
      }
    }
  }
}

/** The login page's button shape, factored out so the three entries here stay identical. */
@Composable
private fun ProviderButton(
  container: Color,
  content: Color,
  icon: @Composable (tint: Color) -> Unit,
  label: String,
  onClick: () -> Unit,
) {
  Button(
    modifier = Modifier
      .fillMaxWidth()
      .height(ProviderButtonHeight),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    colors = ButtonDefaults.buttonColors(
      containerColor = container,
      contentColor = content,
      disabledContainerColor = container.copy(alpha = 0.4f),
      disabledContentColor = content.copy(alpha = 0.4f),
    ),
    onClick = onClick,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      icon(content)
      Text(text = label, style = LoginButtonLabelStyle)
    }
  }
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
