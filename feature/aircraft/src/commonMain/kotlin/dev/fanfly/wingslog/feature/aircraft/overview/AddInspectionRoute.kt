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

  if (successState != null) {
    val aircraftId = successState.aircraft.id
    val allInspections =
      (successState.activeInspections + successState.compliedInspections).map { it.card }

    AddInspectionScreen(
      aircraftId = aircraftId,
      availableInspections = allInspections,
      onCancel = { navController.popBackStack() },
      onSave = { card ->
        viewModel.saveNewInspection(
          title = card.title,
          type = card.type,
          component = card.component,
          rules = emptyList(), // I need to map intervalMonths/Hours back to rules if the VM expects it
          referenceNumber = card.reference_number,
          sbUrl = card.sb_url,
          complianceDetails = "", // Need to handle these if they are relevant
          isOneTime = false, // Map from complianceType if needed
          forceDueDate = card.force_due_date,
          forceDueEngine = card.force_due_engine_hour,
          notes = "" // Map from card if added
        )
        navController.popBackStack()
      }
    )
  }
}
