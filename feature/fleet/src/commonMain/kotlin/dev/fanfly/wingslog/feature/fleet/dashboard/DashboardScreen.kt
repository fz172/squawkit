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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.feature.fleet.dashboard.compose.AircraftDashboardCard
import dev.fanfly.wingslog.feature.fleet.dashboard.data.FleetDashboardViewModel
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.app_name
import wingslog.core.ui.generated.resources.settings
import wingslog.feature.fleet.generated.resources.loading
import wingslog.feature.fleet.generated.resources.no_fleet
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.feature.fleet.generated.resources.Res as FleetRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: FleetDashboardViewModel = koinViewModel(),
  onOpenSettings: () -> Unit,
  onAircraftClick: (String) -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var menuExpanded by remember { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = cmpStringResource(CoreUiRes.string.app_name)) },
        actions = {
          Box {
            IconButton(onClick = { menuExpanded = true }) {
              Icon(
                Icons.Default.MoreVert,
                contentDescription = cmpStringResource(CoreUiRes.string.settings)
              )
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false }
            ) {
              DropdownMenuItem(
                text = { Text(cmpStringResource(CoreUiRes.string.settings)) },
                onClick = {
                  menuExpanded = false
                  onOpenSettings()
                }
              )
            }
          }
        }
      )
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
        Text(
          cmpStringResource(FleetRes.string.loading),
          style = MaterialTheme.typography.headlineMedium
        )
      } else if (uiState.fleet.isEmpty()) {
        Text(
          cmpStringResource(FleetRes.string.no_fleet),
          style = MaterialTheme.typography.headlineMedium
        )
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
