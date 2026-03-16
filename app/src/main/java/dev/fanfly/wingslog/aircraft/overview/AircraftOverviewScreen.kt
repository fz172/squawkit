package dev.fanfly.wingslog.aircraft.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.aircraft.overview.compose.ConfigurationCard
import dev.fanfly.wingslog.aircraft.overview.compose.InspectionCard
import dev.fanfly.wingslog.aircraft.overview.data.AircraftOverviewEvent
import dev.fanfly.wingslog.aircraft.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.aircraft.overview.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.aircraft.overview.data.LogStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewScreen(
  navController: NavController, viewModel: AircraftOverviewViewModel = hiltViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  val successState = uiState as? AircraftOverviewUiState.Success
  val aircraft = successState?.aircraft

  var showSettingsMenu by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        AircraftOverviewEvent.NavigateBack -> navController.popBackStack()
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(R.string.delete_aircraft)) },
      text = { Text(stringResource(R.string.this_action_cannot_be_undone)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteAircraft()
            showDeleteDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(R.string.delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(android.R.string.cancel))
        }
      })
  }

  Scaffold(topBar = {
    TopAppBar(
      title = {
        if (aircraft != null) {
          Column {
            Text(
              text = "${aircraft.make} ${aircraft.model}",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = aircraft.tailNumber,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }, navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)
          )
        }
      }, actions = {
        IconButton(onClick = { showSettingsMenu = !showSettingsMenu }) {
          Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
        }
        DropdownMenu(
          expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.edit_aircraft)) }, onClick = {
            showSettingsMenu = false
            if (aircraft != null) {
              navController.navigate("edit_aircraft/${aircraft.id}")
            }
          })
          DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = {
              showSettingsMenu = false
              showDeleteDialog = true
            })
        }
      }, colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background
      )
    )
  }, floatingActionButton = {
    ExtendedFloatingActionButton(
      text = { Text("Log details") },
      icon = { Icon(Icons.Default.Info, contentDescription = null) },
      onClick = { if (aircraft != null) navController.navigate("maintenance_logs/${aircraft.id}") },
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
  }) { paddingValues ->
    if (aircraft != null) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {

        // --- Configuration Section ---
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          ConfigurationCard(aircraft)
        }

        // --- Log Stats Section ---
        successState?.logStats?.let { stats ->
          LogStatsSection(stats = stats, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // --- Inspection Grid ---
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Text(
            text = stringResource(R.string.inspection_status),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        InspectionGrid()

        Spacer(Modifier.height(80.dp)) // Clearance for FAB
      }
    }
  }
}

@Composable
private fun LogStatsSection(stats: LogStats, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Maintenance Summary",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StatCard(label = "Total Logs", value = stats.total.toString(), modifier = Modifier.weight(1f))
      StatCard(
        label = "Airframe",
        value = stats.airframe.toString(),
        modifier = Modifier.weight(1f)
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      StatCard(label = "Engine", value = stats.engine.toString(), modifier = Modifier.weight(1f))
      StatCard(
        label = "Propeller",
        value = stats.propeller.toString(),
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun InspectionGrid() {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = Modifier.height(360.dp) // fixed height since inside verticalScroll
  ) {
    item {
      InspectionCard(
        title = "100 Hr",
        status = "Due in 14h",
        icon = Icons.Default.Schedule,
        statusColor = androidx.compose.ui.graphics.Color(0xFFFFD54F)
      )
    }
    item {
      InspectionCard(
        title = "Annual",
        status = "Due Dec 2024",
        icon = Icons.Default.CalendarToday,
        statusColor = androidx.compose.ui.graphics.Color(0xFFA5D6A7)
      )
    }
    item {
      InspectionCard(
        title = "Pitot-Static",
        status = "Due in 14h",
        icon = Icons.Default.Speed,
        statusColor = androidx.compose.ui.graphics.Color(0xFFFFD54F)
      )
    }
    item {
      InspectionCard(
        title = "Transponder",
        status = "Due Dec 2024",
        icon = Icons.Default.Radio,
        statusColor = androidx.compose.ui.graphics.Color(0xFFA5D6A7)
      )
    }
  }
}
