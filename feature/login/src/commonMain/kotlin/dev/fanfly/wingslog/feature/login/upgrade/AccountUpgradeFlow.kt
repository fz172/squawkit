package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.account_upgrade_error
import wingslog.feature.login.generated.resources.account_upgrade_success
import wingslog.feature.login.generated.resources.account_upgrade_working

/**
 * The whole guest → permanent account experience: the provider sheet, the email legs, the
 * confirmation gate, and the blocking progress dialog.
 *
 * Lives here rather than in feature/settings because it *is* a sign-in experience — it reuses the
 * login page's buttons, styles and strings, and shares the email-link plumbing with
 * [dev.fanfly.wingslog.feature.login.EmailSignInScreen].
 *
 * Hosted by the shell, not by Settings: an email upgrade link reopens the app on whatever
 * destination it starts at, so this has to stay mounted for the whole signed-in session or the link
 * is never seen. Settings owns only the entry point — it calls [AccountUpgradeViewModel.choose]
 * from its account row, on the same ViewModel instance the shell renders.
 *
 * [onMessage] receives already-resolved text so the host needs no string resources of its own, and
 * this composable stays free of any particular chrome. Hosts that display account state should
 * observe [AccountUpgradeViewModel.completions] rather than relying on a callback here — the flow
 * outlives any one screen, so a success can land while that screen is not composed.
 */
@Composable
fun AccountUpgradeFlow(
  viewModel: AccountUpgradeViewModel = koinViewModel(),
  onMessage: (String) -> Unit,
) {
  val successMessage = stringResource(Res.string.account_upgrade_success)
  val errorMessage = stringResource(Res.string.account_upgrade_error)

  val state by viewModel.state.collectAsStateWithLifecycle()

  // An email link that reopened the app is offered to the flow, which claims it only when this
  // guest has an upgrade pending. Anything else is left untouched for AuthFlow's sign-in path.
  val pendingLink by EmailLinkDeepLinks.pendingLink.collectAsStateWithLifecycle()
  LaunchedEffect(pendingLink) {
    pendingLink?.let(viewModel::onIncomingLink)
  }

  LaunchedEffect(state) {
    when (state) {
      is UpgradeUiState.Success -> {
        onMessage(successMessage)
        viewModel.dismiss()
      }

      is UpgradeUiState.Error -> {
        // The provider's own message is logged, not shown: it is Firebase's wording, not ours.
        onMessage(errorMessage)
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
          Text(stringResource(Res.string.account_upgrade_working))
        }
      },
    )

    else -> Unit
  }
}
