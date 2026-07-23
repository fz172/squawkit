package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessScreen
import dev.fanfly.wingslog.feature.subscription.viewing.ProUpsellSheet
import dev.fanfly.wingslog.feature.subscription.viewing.UpsellTrigger
import org.koin.compose.viewmodel.koinViewModel

/** Binds [ManageAccessViewModel] to [ManageAccessScreen]; registered on the shell nav graph. */
@Composable
fun ManageAccessRoute(navController: NavController) {
  val viewModel = koinViewModel<ManageAccessViewModel>()
  val state by viewModel.uiState.collectAsState()

  // Leaving removes this aircraft from the user's fleet — pop back once it succeeds. Being revoked
  // while the screen is open is the same ending, arrived at from the other side: we are no longer a
  // member, so the roster on screen is stale and must not stay up.
  LaunchedEffect(state.leaveSuccess, state.accessRevoked) {
    if (state.leaveSuccess || state.accessRevoked) navController.popBackStack()
  }

  // Hosting a share is Pro-only: when locked, the owner's Invite action opens the promo instead of
  // navigating (gate as promo). Accepting an invite and managing an existing share stay ungated.
  var showUpsell by remember { mutableStateOf(false) }

  ManageAccessScreen(
    state = state,
    onChangeRole = viewModel::changeRole,
    onRevoke = viewModel::revoke,
    onLeave = viewModel::leave,
    onInvite = {
      if (state.canHostShare) {
        navController.navigate(Screen.InviteToAircraft.createRoute(viewModel.aircraftId))
      } else {
        showUpsell = true
      }
    },
    onBack = { navController.popBackStack() },
  )

  if (showUpsell) {
    ProUpsellSheet(
      trigger = UpsellTrigger.SHARE_HOST,
      onSeePlans = {
        showUpsell = false
        navController.navigate(Screen.Subscription.route)
      },
      onDismiss = { showUpsell = false },
    )
  }
}
