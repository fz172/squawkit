package dev.fanfly.wingslog.feature.aircraft.maintenance.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.compose.MaintenanceLogCard
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListEvent
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListUiState
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListViewModel
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.aircraft.generated.resources.add_first_maintenance_log
import wingslog.feature.aircraft.generated.resources.add_log
import wingslog.feature.aircraft.generated.resources.failed_to_load_logs
import wingslog.feature.aircraft.generated.resources.maintenance_logs
import wingslog.feature.aircraft.generated.resources.no_maintenance_logs_description
import wingslog.feature.aircraft.generated.resources.no_maintenance_logs_title
import wingslog.feature.aircraft.generated.resources.retry
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogListScreen(
  navController: NavController,
  viewModel: MaintenanceLogListViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

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
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      WingsLogTopAppBar(
        title = cmpStringResource(AircraftRes.string.maintenance_logs),
        onBackClick = { navController.popBackStack() },
        scrollBehavior = scrollBehavior
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
        MaintenanceLogListUiState.Error -> Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            cmpStringResource(AircraftRes.string.failed_to_load_logs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Button(onClick = { viewModel.retryLoading() }) {
            Text(cmpStringResource(AircraftRes.string.retry))
          }
        }

        is MaintenanceLogListUiState.Success -> {
          if (state.logs.isEmpty()) {
            EmptyState(
              title = cmpStringResource(AircraftRes.string.no_maintenance_logs_title),
              description = cmpStringResource(AircraftRes.string.no_maintenance_logs_description),
              icon = Icons.Default.History,
              actionText = cmpStringResource(AircraftRes.string.add_first_maintenance_log),
              onActionClick = { viewModel.onAddLog() }
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
