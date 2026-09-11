package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.LoginCard
import dev.fanfly.wingslog.feature.login.LoginRow
import dev.fanfly.wingslog.feature.login.LoginRowDivider
import dev.fanfly.wingslog.feature.login.LoginButtonHeight
import dev.fanfly.wingslog.feature.login.LoginButtonLabelStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.provider_apple
import wingslog.feature.login.generated.resources.provider_email
import wingslog.feature.login.generated.resources.provider_google
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.ic_google_rd_na
import wingslog.feature.login.generated.resources.sign_in_with_email
import wingslog.feature.login.generated.resources.upgrade_confirm_link_body
import wingslog.feature.login.generated.resources.upgrade_confirm_link_confirm
import wingslog.feature.login.generated.resources.upgrade_confirm_link_title
import wingslog.feature.login.generated.resources.upgrade_email_body
import wingslog.feature.login.generated.resources.upgrade_email_invalid
import wingslog.feature.login.generated.resources.upgrade_email_label
import wingslog.feature.login.generated.resources.upgrade_email_send
import wingslog.feature.login.generated.resources.upgrade_email_send_failed
import wingslog.feature.login.generated.resources.upgrade_email_title
import wingslog.feature.login.generated.resources.upgrade_link_sent_body
import wingslog.feature.login.generated.resources.upgrade_link_sent_title
import wingslog.feature.login.generated.resources.upgrade_merge_body
import wingslog.feature.login.generated.resources.upgrade_merge_confirm
import wingslog.feature.login.generated.resources.upgrade_merge_reauth
import wingslog.feature.login.generated.resources.upgrade_merge_title
import wingslog.feature.login.generated.resources.upgrade_picker_body
import wingslog.feature.login.generated.resources.upgrade_picker_title
import wingslog.feature.login.generated.resources.upgrade_provider_apple
import wingslog.feature.login.generated.resources.upgrade_provider_email
import wingslog.feature.login.generated.resources.upgrade_provider_google
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.done
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

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
        text = stringResource(Res.string.upgrade_picker_title),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = stringResource(Res.string.upgrade_picker_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(Spacing.small))

      // The same card and rows the login page draws, so the two surfaces cannot drift. No heading:
      // the sheet's own title is right above it.
      LoginCard {
        providers.forEachIndexed { index, provider ->
          if (index > 0) LoginRowDivider()
          when (provider) {
            AuthProvider.Google -> LoginRow(
              label = stringResource(Res.string.provider_google),
              enabled = true,
              onClick = { onSelect(AuthProvider.Google) },
              icon = {
                // The multi-colour mark must not be tinted.
                Icon(
                  painter = painterResource(Res.drawable.ic_google_rd_na),
                  contentDescription = stringResource(Res.string.google_logo),
                  modifier = Modifier.size(16.dp),
                  tint = Color.Unspecified,
                )
              },
            )

            AuthProvider.Apple -> LoginRow(
              label = stringResource(Res.string.provider_apple),
              enabled = true,
              onClick = { onSelect(AuthProvider.Apple) },
              icon = {
                Icon(
                  painter = painterResource(Res.drawable.ic_apple),
                  contentDescription = stringResource(Res.string.apple_logo),
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.onSurface,
                )
              },
            )

            // Email opens an address form rather than signing in. It used to be styled quieter to
            // say so; the row's chevron says it now, the same as on the login page.
            AuthProvider.Email -> LoginRow(
              label = stringResource(Res.string.provider_email),
              enabled = true,
              onClick = { onSelect(AuthProvider.Email) },
              icon = {
                Icon(
                  imageVector = Icons.Filled.Email,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
            )
          }
        }
      }
    }
  }
}

/**
 * The interstitial for a collision: this provider account already has SquawkIt records.
 *
 * A bottom sheet rather than a dialog so it reads as a continuation of the picker the user just
 * used. Its real job on iOS is to give the second Apple sheet a reason — without it, that prompt
 * looks like the first one silently failed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeMergeSheet(
  provider: AuthProvider,
  needsReauthorization: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val name = stringResource(
    when (provider) {
      AuthProvider.Apple -> Res.string.upgrade_provider_apple
      AuthProvider.Google -> Res.string.upgrade_provider_google
      AuthProvider.Email -> Res.string.upgrade_provider_email
    }
  )

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.large)
        .padding(bottom = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(Res.string.upgrade_merge_title, name),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = stringResource(Res.string.upgrade_merge_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (needsReauthorization) {
        Text(
          text = stringResource(Res.string.upgrade_merge_reauth, name),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(Spacing.small))

      Button(
        modifier = Modifier
          .fillMaxWidth()
          .height(LoginButtonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        onClick = onConfirm,
      ) {
        Text(
          text = stringResource(Res.string.upgrade_merge_confirm),
          style = LoginButtonLabelStyle,
        )
      }
      OutlinedButton(
        modifier = Modifier
          .fillMaxWidth()
          .height(LoginButtonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        onClick = onDismiss,
      ) {
        Text(
          text = stringResource(CoreRes.string.cancel),
          style = LoginButtonLabelStyle,
        )
      }
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
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
