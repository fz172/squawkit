package dev.fanfly.wingslog.feature.fleet.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.FleetDashboardViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.app_name
import wingslog.core.ui.generated.resources.settings
import wingslog.feature.fleet.sharedassets.generated.resources.add_aircraft
import wingslog.feature.fleet.sharedassets.generated.resources.add_first_aircraft
import wingslog.feature.fleet.sharedassets.generated.resources.no_fleet_description
import wingslog.feature.fleet.sharedassets.generated.resources.no_fleet_title
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.feature.fleet.sharedassets.generated.resources.Res as FleetRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: FleetDashboardViewModel = koinViewModel(),
  onOpenSettings: () -> Unit,
  onAddAircraft: () -> Unit,
  onAircraftClick: (String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var hasAutoForwarded by rememberSaveable { mutableStateOf(false) }

  // Auto-forward for single-aircraft owners on cold start
  LaunchedEffect(uiState.fleet, uiState.isLoading) {
    if (!uiState.isLoading && uiState.fleet.size == 1 && !hasAutoForwarded) {
      hasAutoForwarded = true
      onAircraftClick(uiState.fleet.first().id)
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(title = { Text(text = stringResource(CoreUiRes.string.app_name)) }, actions = {
        IconButton(onClick = onOpenSettings) {
          Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(CoreUiRes.string.settings)
          )
        }
      })
    },
    floatingActionButton = {
      if (!uiState.isLoading && uiState.fleet.isNotEmpty()) {
        FloatingActionButton(
          onClick = onAddAircraft,
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
          Icon(
            Icons.Default.Add,
            contentDescription = stringResource(FleetRes.string.add_aircraft)
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center
    ) {
      if (uiState.isLoading) {
        CircularProgressIndicator()
      } else if (uiState.fleet.isEmpty()) {
        EmptyState(
          title = stringResource(FleetRes.string.no_fleet_title),
          description = stringResource(FleetRes.string.no_fleet_description),
          icon = Icons.Default.AirplanemodeActive,
          actionText = stringResource(FleetRes.string.add_first_aircraft),
          onActionClick = onAddAircraft
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(Spacing.screenPadding),
          verticalArrangement = Arrangement.spacedBy(Spacing.columnGap)
        ) {
          items(uiState.fleet, key = { it.id }) { aircraft ->
            AircraftDashboardCard(
              aircraft = aircraft,
              onClick = { aircraftId -> onAircraftClick(aircraftId) },
              healthStatus = uiState.aircraftHealthStatus[aircraft.id]
            )
          }
        }
      }
    }
  }
}
