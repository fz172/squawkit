package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole guest → permanent account experience: the provider sheet, the email legs, the
 * confirmation gate, and the blocking progress dialog.
 *
 * Lives here rather than in feature/settings because it *is* a sign-in experience — it reuses the
 * login page's buttons, styles and strings, and shares the email-link plumbing with
 * [dev.fanfly.wingslog.feature.login.EmailSignInScreen]. Settings only owns the entry point: it
 * renders this and calls [AccountUpgradeViewModel.choose] from its account row.
 *
 * [onCompleted] fires once an upgrade succeeds, so the host can refresh whatever it shows about the
 * account. [onMessage] carries user-facing success/failure text for the host's snackbar, keeping
 * this composable free of any particular chrome.
 */
@Composable
fun AccountUpgradeFlow(
  viewModel: AccountUpgradeViewModel = koinViewModel(),
  onCompleted: () -> Unit,
  onMessage: (UpgradeMessage) -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()

  // An email link that reopened the app is offered to the flow, which claims it only when this
  // guest has an upgrade pending. Anything else is left untouched for AuthFlow's sign-in path.
  val pendingLink by EmailLinkDeepLinks.pendingLink.collectAsStateWithLifecycle()
  LaunchedEffect(pendingLink) {
    pendingLink?.let(viewModel::onIncomingLink)
  }

  LaunchedEffect(state) {
    when (val current = state) {
      is UpgradeUiState.Success -> {
        onCompleted()
        onMessage(UpgradeMessage.Success)
        viewModel.dismiss()
      }

      is UpgradeUiState.Error -> {
        onMessage(UpgradeMessage.Failure(current.message))
        viewModel.dismiss()
      }

      else -> Unit
    }
  }

  when (val current = state) {
    is UpgradeUiState.ChoosingProvider -> UpgradeProviderSheet(
      providers = current.providers,
      onSelect = viewModel::select,
      onDismiss = viewModel::cancel,
    )

    is UpgradeUiState.EnteringEmail -> UpgradeEmailDialog(
      state = current,
      onEmailChange = viewModel::setEmail,
      onSend = viewModel::sendEmailLink,
      onDismiss = viewModel::cancel,
    )

    is UpgradeUiState.LinkSent -> UpgradeLinkSentDialog(
      email = current.email,
      onDismiss = viewModel::cancel,
    )

    is UpgradeUiState.ConfirmLink -> UpgradeConfirmLinkDialog(
      email = current.email,
      onConfirm = viewModel::confirmEmailLink,
      onDismiss = viewModel::cancel,
    )

    is UpgradeUiState.ConfirmMerge -> UpgradeMergeSheet(
      provider = current.provider,
      needsReauthorization = current.needsReauthorization,
      onConfirm = viewModel::confirmMerge,
      onDismiss = viewModel::cancel,
    )

    is UpgradeUiState.Working -> AlertDialog(
      // Non-dismissable: provider sign-in / sync re-keying is in flight.
      onDismissRequest = {},
      confirmButton = {},
      text = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(Spacing.xLarge))
          Text("Syncing your records…")
        }
      },
    )

    else -> Unit
  }
}

/** What the host should tell the user. Text for the failure case comes from the auth layer. */
sealed interface UpgradeMessage {
  data object Success : UpgradeMessage
  data class Failure(val message: String) : UpgradeMessage
}
