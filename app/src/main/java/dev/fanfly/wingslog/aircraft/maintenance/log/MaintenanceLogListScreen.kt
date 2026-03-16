package dev.fanfly.wingslog.aircraft.maintenance.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.fanfly.wingslog.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.aircraft.maintenance.log.compose.MaintenanceLogCard
import dev.fanfly.wingslog.aircraft.maintenance.log.data.MaintenanceLogListEvent
import dev.fanfly.wingslog.aircraft.maintenance.log.data.MaintenanceLogListUiState
import dev.fanfly.wingslog.aircraft.maintenance.log.data.MaintenanceLogListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogListScreen(
    navController: NavController,
    viewModel: MaintenanceLogListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDeleteLogId by remember { mutableStateOf<String?>(null) }

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
                title = { Text(stringResource(R.string.maintenance_logs)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddLog() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_log))
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
                    stringResource(R.string.failed_to_load_logs),
                    style = MaterialTheme.typography.bodyLarge
                )
                is MaintenanceLogListUiState.Success -> {
                    if (state.logs.isEmpty()) {
                        Text(
                            stringResource(R.string.no_maintenance_logs_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    onEdit = { viewModel.onEditLog(log.id) },
                                    onDelete = { pendingDeleteLogId = log.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    pendingDeleteLogId?.let { logId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLogId = null },
            title = { Text(stringResource(R.string.delete_log)) },
            text = { Text(stringResource(R.string.this_action_cannot_be_undone)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLog(logId)
                        pendingDeleteLogId = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLogId = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
