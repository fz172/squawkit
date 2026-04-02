package dev.fanfly.wingslog.feature.aircraft.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.aircraft.overview.compose.EditInspectionScreen
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditInspectionRoute(
  cardId: String,
  navController: NavController,
  viewModel: AircraftOverviewViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val successState = uiState as? AircraftOverviewUiState.Success
  
  // Find the card in the list
  val cardWithStatus = successState?.activeInspections?.find { it.card.id == cardId }
    ?: successState?.compliedInspections?.find { it.card.id == cardId }

  if (cardWithStatus != null && successState != null) {
    val allInspections = (successState.activeInspections + successState.compliedInspections).map { it.card }

    EditInspectionScreen(
      card = cardWithStatus.card,
      availableInspections = allInspections,
      onCancel = { navController.popBackStack() },
      onSave = { updatedCard ->
        viewModel.saveEditedInspection(
          cardId = updatedCard.id,
          title = updatedCard.title,
          type = updatedCard.type,
          component = updatedCard.component,
          rules = updatedCard.rules,
          referenceNumber = updatedCard.reference_number,
          sbUrl = updatedCard.sb_url,
          complianceDetails = updatedCard.compliance_details,
          isOneTime = updatedCard.is_one_time,
          forceDueDate = updatedCard.force_due_date,
          forceDueEngine = updatedCard.force_due_engine_hour,
          notes = updatedCard.notes
        )
        navController.popBackStack()
      },
      onDeleteRequest = { id ->
        viewModel.requestDeleteInspection(id)
        navController.popBackStack()
      }
    )
  }
}
