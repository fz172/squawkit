package dev.fanfly.wingslog.aircraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.aircraft.compose.ConfigurationCard
import dev.fanfly.wingslog.aircraft.compose.InspectionCard
import dev.fanfly.wingslog.aircraft.data.AircraftOverviewEvent
import dev.fanfly.wingslog.aircraft.data.AircraftOverviewUiState
import dev.fanfly.wingslog.aircraft.data.AircraftOverviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewScreen(
  navController: NavController, viewModel: AircraftOverviewViewModel = hiltViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  val aircraft = (uiState as? AircraftOverviewUiState.Success)?.aircraft

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
      text = { Text(stringResource(R.string.add_log)) },
      icon = { Icon(Icons.Default.Add, contentDescription = null) },
      onClick = { /* TODO: Add Log */ },
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
  }) { paddingValues ->
    if (aircraft != null) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(scrollState)
          .padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)
      ) {
        // --- Header Status Badge ---
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Placeholder for status if needed, or just keep it in header.
          // Mock showed "Airworthy" badge.
          StatusBadge(status = "Airworthy", isGood = true)
        }

        // --- Inspection Grid ---
        Text(
          text = stringResource(R.string.inspection_status),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        InspectionGrid()

        // --- Configuration Section ---
        Text(
          text = stringResource(R.string.configuration),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        ConfigurationCard(aircraft)

        Spacer(Modifier.height(80.dp)) // Clearance for FAB
      }
    }
  }
}

@Composable
fun StatusBadge(status: String, isGood: Boolean) {
  val containerColor = if (isGood) Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer
  val contentColor = if (isGood) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onErrorContainer

  Surface(
    color = containerColor, shape = RoundedCornerShape(16.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = status,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun InspectionGrid() {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      InspectionCard(
        title = "100 Hr",
        status = "Due in 14h",
        icon = Icons.Default.Schedule,
        statusColor = Color(0xFFFFD54F), // Yellowish
        modifier = Modifier.weight(1f)
      )
      InspectionCard(
        title = "Annual",
        status = "Due Dec 2024",
        icon = Icons.Default.CalendarToday,
        statusColor = Color(0xFFA5D6A7), // Greenish
        modifier = Modifier.weight(1f)
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      InspectionCard(
        title = "Pitot-Static", status = "Due in 14h", // Mock data
        icon = Icons.Default.Speed, statusColor = Color(0xFFFFD54F), modifier = Modifier.weight(1f)
      )
      InspectionCard(
        title = "Transponder",
        status = "Due Dec 2024",
        icon = Icons.Default.Radio,
        statusColor = Color(0xFFA5D6A7),
        modifier = Modifier.weight(1f)
      )
    }
  }
}

