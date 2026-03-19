package dev.fanfly.wingslog.feature.fleet.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import dev.fanfly.wingslog.feature.fleet.R
import dev.fanfly.wingslog.feature.fleet.dashboard.compose.AircraftDashboardCard
import dev.fanfly.wingslog.feature.fleet.dashboard.data.FleetDashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: FleetDashboardViewModel = koinViewModel(),
  onOpenSettings: () -> Unit,
  onClickFab: () -> Unit,
  onAircraftClick: (String) -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var menuExpanded by remember { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = cmpStringResource(Res.string.app_name)) },
        actions = {
          Box {
            IconButton(onClick = { menuExpanded = true }) {
              Icon(Icons.Default.MoreVert, contentDescription = cmpStringResource(Res.string.settings))
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false }
            ) {
              DropdownMenuItem(
                text = { Text(cmpStringResource(Res.string.settings)) },
                onClick = {
                  menuExpanded = false
                  onOpenSettings()
                }
              )
            }
          }
        }
      )
    },
    floatingActionButton = {
      // Only show FAB if fleet is empty
      if (uiState.isLoading || uiState.fleet.isEmpty()) {
        ExtendedFloatingActionButton(
          onClick = { onClickFab() },
          icon = { Icon(Icons.Default.Build, contentDescription = null) },
          text = { Text(stringResource(R.string.add_aircraft)) },
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.Center
    ) {
      // Only show the text if isLoading is false
      if (uiState.isLoading) {
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.headlineMedium)
      } else if (uiState.fleet.isEmpty()) {
        Text(stringResource(R.string.no_fleet), style = MaterialTheme.typography.headlineMedium)
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          items(uiState.fleet, key = { it.id }) { aircraft ->
            AircraftDashboardCard(aircraft, onClick = { aircraftId ->
              onAircraftClick(aircraftId)
            })
          }
        }
      }
    }
  }
}
