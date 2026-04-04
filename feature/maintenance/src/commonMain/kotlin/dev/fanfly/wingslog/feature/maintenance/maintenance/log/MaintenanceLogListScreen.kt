package dev.fanfly.wingslog.feature.maintenance.maintenance.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.maintenance.maintenance.log.compose.MaintenanceLogCard
import dev.fanfly.wingslog.feature.maintenance.maintenance.log.data.MaintenanceLogListEvent
import dev.fanfly.wingslog.feature.maintenance.maintenance.log.data.MaintenanceLogListUiState
import dev.fanfly.wingslog.feature.maintenance.maintenance.log.data.MaintenanceLogListViewModel
import dev.fanfly.wingslog.feature.maintenance.maintenance.util.displayName
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.all
import wingslog.core.ui.generated.resources.retry
import wingslog.feature.maintenance.generated.resources.add_first_maintenance_log
import wingslog.feature.maintenance.generated.resources.add_log
import wingslog.feature.maintenance.generated.resources.clear_filter
import wingslog.feature.maintenance.generated.resources.failed_to_load_logs
import wingslog.feature.maintenance.generated.resources.maintenance_logs
import wingslog.feature.maintenance.generated.resources.no_logs_match_filter
import wingslog.feature.maintenance.generated.resources.no_maintenance_logs_description
import wingslog.feature.maintenance.generated.resources.no_maintenance_logs_title
import wingslog.feature.maintenance.generated.resources.search_logs
import wingslog.feature.maintenance.generated.resources.showing_x_of_y
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.maintenance.generated.resources.Res as MaintenanceRes

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
        title = cmpStringResource(MaintenanceRes.string.maintenance_logs),
        onBackClick = { navController.popBackStack() },
        scrollBehavior = scrollBehavior
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { viewModel.onAddLog() }) {
        Icon(
          Icons.Default.Add,
          contentDescription = cmpStringResource(MaintenanceRes.string.add_log)
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
      when (val state = uiState) {
        MaintenanceLogListUiState.Loading -> CircularProgressIndicator()

        MaintenanceLogListUiState.Error -> Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Text(
            cmpStringResource(MaintenanceRes.string.failed_to_load_logs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Button(onClick = { viewModel.retryLoading() }) {
            Text(cmpStringResource(CoreRes.string.retry))
          }
        }

        is MaintenanceLogListUiState.Success -> {
          if (state.totalCount == 0) {
            EmptyState(
              title = cmpStringResource(MaintenanceRes.string.no_maintenance_logs_title),
              description = cmpStringResource(MaintenanceRes.string.no_maintenance_logs_description),
              icon = Icons.Default.History,
              actionText = cmpStringResource(MaintenanceRes.string.add_first_maintenance_log),
              onActionClick = { viewModel.onAddLog() }
            )
          } else {
            Column(modifier = Modifier.fillMaxSize()) {
              // Search bar
              OutlinedTextField(
                value = state.filter.query,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = Spacing.screenPadding, vertical = Spacing.small),
                placeholder = { Text(cmpStringResource(MaintenanceRes.string.search_logs)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                  if (state.filter.query.isNotBlank()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                      Icon(Icons.Default.Close, contentDescription = null)
                    }
                  }
                },
                singleLine = true,
              )

              // Component filter chips
              val components = listOf(
                null,
                MaintenanceLog.ComponentType.AIRFRAME,
                MaintenanceLog.ComponentType.ENGINE,
                MaintenanceLog.ComponentType.PROPELLER,
              )
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = Spacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
              ) {
                components.forEach { component ->
                  val label = component?.displayName() ?: cmpStringResource(CoreRes.string.all)
                  FilterChip(
                    selected = state.filter.component == component,
                    onClick = { viewModel.onComponentFilterChange(component) },
                    label = { Text(label) }
                  )
                }
              }

              // Result count when filtering
              if (state.filter.isActive) {
                Text(
                  text = cmpStringResource(
                    MaintenanceRes.string
                      .showing_x_of_y,
                    state.logs.size,
                    state.totalCount
                  ),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(
                    horizontal = Spacing.screenPadding,
                    vertical = Spacing.tiny
                  )
                )
              }

              if (state.logs.isEmpty()) {
                // Filter active but no results
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                  contentAlignment = Alignment.Center
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                  ) {
                    Text(
                      text = cmpStringResource(MaintenanceRes.string.no_logs_match_filter),
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { viewModel.clearFilter() }) {
                      Text(cmpStringResource(MaintenanceRes.string.clear_filter))
                    }
                  }
                }
              } else {
                LazyColumn(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                  contentPadding = PaddingValues(
                    start = Spacing.screenPadding,
                    end = Spacing.screenPadding,
                    top = Spacing.small,
                    bottom = 80.dp
                  ),
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  items(state.logs, key = { it.id }) { log ->
                    MaintenanceLogCard(
                      log = log,
                      onClick = { viewModel.onEditLog(log.id) }
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
