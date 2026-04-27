package dev.fanfly.wingslog.feature.aircraft.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.common.navigation.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.AircraftOverviewContent
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewEvent
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.error_occurred


@Composable
fun AircraftOverviewScreen(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  val errorOccurredMessage = stringResource(CoreRes.string.error_occurred)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is AircraftOverviewEvent.NavigateBack -> navController.popBackStack()
        is AircraftOverviewEvent.ShowError -> {
          val message = event.message ?: errorOccurredMessage
          snackbarHostState.showSnackbar(message)
        }

        is AircraftOverviewEvent.NavigateToAddTask ->
          navController.navigate(Screen.AddMaintenanceTask.createRoute(event.aircraftId))

        is AircraftOverviewEvent.NavigateToAddLog ->
          navController.navigate(Screen.AddMaintenanceLog.createRoute(event.aircraftId))

        is AircraftOverviewEvent.NavigateToEditAircraft ->
          navController.navigate(Screen.EditAircraft.createRoute(event.aircraftId))

        is AircraftOverviewEvent.NavigateToEditTask ->
          navController.navigate(
            Screen.EditMaintenanceTask.createRoute(
              event.aircraftId,
              event.cardId
            )
          )

        is AircraftOverviewEvent.NavigateToEditLog ->
          navController.navigate(
            Screen.EditMaintenanceLog.createRoute(
              event.aircraftId,
              event.logId
            )
          )
      }
    }
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  LaunchedEffect(navBackStackEntry) {
    val handle = navBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
    val message = handle.get<String>(CROSS_SCREEN_SUCCESS_MESSAGE).orEmpty()
    if (message.isNotEmpty()) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(message)
      }
      handle[CROSS_SCREEN_SUCCESS_MESSAGE] = ""
    }
  }

  when (val state = uiState) {
    AircraftOverviewUiState.Loading -> {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    }

    AircraftOverviewUiState.Error -> {
    }

    is AircraftOverviewUiState.Success -> {
      AircraftOverviewContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
      )
    }
  }
}
