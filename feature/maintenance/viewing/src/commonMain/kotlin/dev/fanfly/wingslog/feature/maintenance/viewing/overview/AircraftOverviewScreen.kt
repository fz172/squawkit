package dev.fanfly.wingslog.feature.maintenance.viewing.overview

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
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.AircraftOverviewContent
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewEvent
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.error_occurred
import wingslog.core.ui.generated.resources.Res as CoreRes


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

        is AircraftOverviewEvent.NavigateToAddInspection ->
          navController.navigate(Screen.AddInspection.createRoute(event.aircraftId))
        is AircraftOverviewEvent.NavigateToAddLog ->
          navController.navigate(Screen.AddMaintenanceLog.createRoute(event.aircraftId))
        is AircraftOverviewEvent.NavigateToEditAircraft ->
          navController.navigate(Screen.EditAircraft.createRoute(event.aircraftId))
        is AircraftOverviewEvent.NavigateToEditInspection ->
          navController.navigate(Screen.EditInspection.createRoute(event.aircraftId, event.cardId))
        is AircraftOverviewEvent.NavigateToLogDetails ->
          navController.navigate(Screen.MaintenanceLogs.createRoute(event.aircraftId))
      }
    }
  }

// Show success messages from child screens (maintenance log, inspection forms)
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  LaunchedEffect(navBackStackEntry) {
    val handle = navBackStackEntry?.savedStateHandle ?: return@LaunchedEffect
    val message = handle.get<String>("success_message").orEmpty()
    if (message.isNotEmpty()) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(message)
      }
      handle.set("success_message", "")
    }
  }

  when (val state = uiState) {
    AircraftOverviewUiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }

    AircraftOverviewUiState.Error -> {
      // Handle error state
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

