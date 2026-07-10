package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.fanfly.wingslog.feature.sharing.viewing.RedeemConfirmationSheet
import org.koin.compose.viewmodel.koinViewModel

/**
 * App-root overlay for inbound aircraft-share deep links: shows the redeem confirmation dialog
 * whenever an invite is parked. Placed once at each host's root, above the nav graph, so a link
 * that arrives mid-session (or resumes after sign-in) surfaces on top of whatever screen is shown.
 */
@Composable
fun RedeemHost() {
  val viewModel = koinViewModel<RedeemViewModel>()
  val state by viewModel.uiState.collectAsState()
  RedeemConfirmationSheet(
    state = state,
    onAccept = viewModel::accept,
    onDismiss = viewModel::dismiss,
  )
}
