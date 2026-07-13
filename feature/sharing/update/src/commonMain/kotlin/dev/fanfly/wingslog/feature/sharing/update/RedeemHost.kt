package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.feature.sharing.viewing.RedeemConfirmationSheet
import dev.fanfly.wingslog.feature.sharing.viewing.RedeemUiState
import dev.fanfly.wingslog.feature.sharing.viewing.redeemRoleLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_success_snackbar

/**
 * App-root overlay for inbound aircraft-share deep links: shows the redeem confirmation dialog
 * whenever an invite is parked. Placed once at each host's root, above the nav graph, so a link
 * that arrives mid-session (or resumes after sign-in) surfaces on top of whatever screen is shown.
 *
 * Success is a **snackbar**, not a dialog. Joining worked and there is nothing to decide — a modal
 * would make the user dismiss a box before they can look at the aircraft they just joined. It owns
 * its own [SnackbarHostState] because this sits *above* the nav graph and cannot reach the shell's.
 */
@Composable
fun RedeemHost() {
  val viewModel = koinViewModel<RedeemViewModel>()
  val state by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  val success = state as? RedeemUiState.Success
  val message = success?.let { stringResource(Res.string.redeem_success_snackbar, redeemRoleLabel(it.role)) }

  LaunchedEffect(success) {
    val text = message ?: return@LaunchedEffect
    // Clear the parked invite first: the snackbar suspends until it is dismissed or times out, and
    // leaving the invite held that whole time would re-show the sheet if the screen recomposed.
    viewModel.dismiss()
    snackbarHostState.showSnackbar(text)
  }

  RedeemConfirmationSheet(
    state = state,
    onAccept = viewModel::accept,
    onDismiss = viewModel::dismiss,
  )

  Box(modifier = Modifier.fillMaxSize()) {
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding(),
    )
  }
}
