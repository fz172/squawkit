package dev.fanfly.wingslog.feature.aircraft.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.aircraft.overview.compose.AddInspectionScreen
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.aircraft.generated.resources.inspection_added
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@Composable
fun AddInspectionRoute(
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val successState = uiState as? AircraftOverviewUiState.Success

  val successMessage = stringResource(AircraftRes.string.inspection_added)

  if (successState != null) {
    val allInspections =
      (successState.activeInspections + successState.compliedInspections).map { it.card }

    AddInspectionScreen(
      availableInspections = allInspections,
      onCancel = { navController.popBackStack() },
      onSave = { card ->
        viewModel.saveNewInspection(
          title = card.title,
          type = card.type,
          component = card.component,
          rules = card.rules,
          referenceNumber = card.reference_number,
          complianceAuthority = card.compliance_authority,
          complianceDetails = card.compliance_details,
          isOneTime = card.is_one_time,
          forceDueDate = card.force_due_date,
          forceDueEngine = card.force_due_engine_hour,
          notes = card.notes
        )
        navController.previousBackStackEntry?.savedStateHandle?.set(
          "success_message",
          successMessage
        )
        navController.popBackStack()
      }
    )
  }
}
