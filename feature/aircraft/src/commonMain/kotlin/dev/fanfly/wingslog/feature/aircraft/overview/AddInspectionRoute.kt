package dev.fanfly.wingslog.feature.aircraft.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.aircraft.overview.compose.AddInspectionScreen
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AddInspectionRoute(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val successState = uiState as? AircraftOverviewUiState.Success
  
  val recurringInspections = (successState?.activeInspections ?: emptyList())
    .map { it.card }
    .filter { it.type == dev.fanfly.wingslog.aircraft.ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION }

  AddInspectionScreen(
    availableRecurringInspections = recurringInspections,
    onBackClick = { navController.popBackStack() },
    onSave = { title, type, component, rules, refNum, url, details, oneTime, notes ->
      viewModel.saveNewInspection(title, type, component, rules, refNum, url, details, oneTime, notes)
      navController.popBackStack()
    }
  )
}
