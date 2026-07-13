package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessScreen
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

  ManageAccessScreen(
    state = state,
    onChangeRole = viewModel::changeRole,
    onRevoke = viewModel::revoke,
    onLeave = viewModel::leave,
    onInvite = {
      navController.navigate(Screen.InviteToAircraft.createRoute(viewModel.aircraftId))
    },
    onBack = { navController.popBackStack() },
  )
}
