package dev.fanfly.wingslog.aircraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.fanfly.wingslog.aircraft.compose.InspectionCard
import dev.fanfly.wingslog.aircraft.data.AircraftOverviewUiState
import dev.fanfly.wingslog.aircraft.data.AircraftOverviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftOverviewScreen(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = hiltViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollState = rememberScrollState()

  val aircraft = (uiState as? AircraftOverviewUiState.Success)?.aircraft

  Scaffold(
    topBar = {
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
        },
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
             IconButton(onClick = { /* TODO: Settings/Edit */ }) {
                 Icon(Icons.Default.Settings, contentDescription = "Settings")
             }
        },
        colors = TopAppBarDefaults.topAppBarColors(
             containerColor = MaterialTheme.colorScheme.background,
             scrolledContainerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    floatingActionButton = {
        ExtendedFloatingActionButton(
            text = { Text(stringResource(R.string.add_log)) },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = { /* TODO: Add Log */ },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
  ) { paddingValues ->
    if (aircraft != null) {
        Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .verticalScroll(scrollState)
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
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
            text = "Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ConfigurationCard(aircraft)
        
        Spacer(Modifier.height(80.dp)) // Clearance for FAB
        }
    } else {
        // Loading or Empty state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             Text("Loading...")
        }
    }
  }
}

@Composable
fun StatusBadge(status: String, isGood: Boolean) {
    val containerColor = if (isGood) Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isGood) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.onErrorContainer
    
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
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
              title = "Pitot-Static",
              status = "Due in 14h", // Mock data
              icon = Icons.Default.Speed,
              statusColor = Color(0xFFFFD54F),
              modifier = Modifier.weight(1f)
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

@Composable
fun ConfigurationCard(aircraft: Aircraft) {
   Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
  ) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Airframe
        Column {
             Text(
                text = "Airframe S/N: ${aircraft.serial}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Engines
        aircraft.engineList.forEach { engine ->
            EngineDetails(engine)
        }
    }
  }
}

@Composable
fun EngineDetails(engine: Engine) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // Engine Header
    Column {
        Text(
          text = "Engine 1", // Assuming single engine for now or iterating 1
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "Model: ${engine.make} ${engine.model}\nS/N: ${engine.serial}\nTSO: 1250.4h", // Mock TSO
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Propeller Details
    Column {
        Text(
          text = "Propeller",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Model: ${engine.propeller.hub.make} ${engine.propeller.hub.model}\nS/N: ${engine.propeller.hub.serial}\nTSO: 1250.4h",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    // Blades
    if (engine.propeller.bladesList.isNotEmpty()) {
         BladeChipsOverview(engine.propeller.bladesList)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BladeChipsOverview(blades: List<PropellerBlade>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blades.forEachIndexed { index, blade ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "B${index + 1}: ${blade.serial}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
