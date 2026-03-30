package dev.fanfly.wingslog.feature.aircraft.maintenance.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.EmptyStateText
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.compose.MaintenanceLogCard
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListEvent
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListUiState
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListViewModel
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.aircraft.generated.resources.add_log
import wingslog.feature.aircraft.generated.resources.back
import wingslog.feature.aircraft.generated.resources.failed_to_load_logs
import wingslog.feature.aircraft.generated.resources.maintenance_logs
import wingslog.feature.aircraft.generated.resources.no_maintenance_logs_hint
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogListScreen(
  navController: NavController,
  viewModel: MaintenanceLogListViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is MaintenanceLogListEvent.NavigateToCreateLog ->
          navController.navigate("maintenance_log_create/${event.aircraftId}")

        is MaintenanceLogListEvent.NavigateToEditLog ->
          navController.navigate("maintenance_log_edit/${event.aircraftId}/${event.logId}")
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(cmpStringResource(AircraftRes.string.maintenance_logs)) },
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = cmpStringResource(AircraftRes.string.back)
            )
          }
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { viewModel.onAddLog() }) {
        Icon(Icons.Default.Add, contentDescription = cmpStringResource(AircraftRes.string.add_log))
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.Center
    ) {
      when (val state = uiState) {
        MaintenanceLogListUiState.Loading -> CircularProgressIndicator()
        MaintenanceLogListUiState.Error -> Text(
          cmpStringResource(AircraftRes.string.failed_to_load_logs),
          style = MaterialTheme.typography.bodyLarge
        )

        is MaintenanceLogListUiState.Success -> {
          if (state.logs.isEmpty()) {
            EmptyStateText(
              text = cmpStringResource(AircraftRes.string.no_maintenance_logs_hint),
              modifier = Modifier.padding(16.dp)
            )
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              items(state.logs, key = { it.id }) { log ->
                MaintenanceLogCard(
                  log = log,
                  onEdit = { viewModel.onEditLog(log.id) }
                )
              }
            }
          }
        }
      }
    }
  }

}
