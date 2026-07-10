package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessScreen
import org.koin.compose.viewmodel.koinViewModel

/** Binds [ManageAccessViewModel] to [ManageAccessScreen]; registered on the shell nav graph. */
@Composable
fun ManageAccessRoute(navController: NavController) {
  val viewModel = koinViewModel<ManageAccessViewModel>()
  val state by viewModel.uiState.collectAsState()

  // Leaving removes this aircraft from the user's fleet — pop back once it succeeds.
  LaunchedEffect(state.leaveSuccess) {
    if (state.leaveSuccess) navController.popBackStack()
  }

  ManageAccessScreen(
    state = state,
    onChangeRole = viewModel::changeRole,
    onRevoke = viewModel::revoke,
    onLeave = viewModel::leave,
    onBack = { navController.popBackStack() },
  )
}
