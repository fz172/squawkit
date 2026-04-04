package dev.fanfly.wingslog.feature.inspection.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.inspection.viewmodel.InspectionUiState
import dev.fanfly.wingslog.feature.inspection.viewmodel.InspectionViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.inspection.generated.resources.inspection_deleted
import wingslog.feature.inspection.generated.resources.inspection_updated
import wingslog.feature.inspection.generated.resources.Res as InspectionRes

@Composable
fun EditInspectionRoute(
  navController: NavController,
  viewModel: InspectionViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val successState = uiState as? InspectionUiState.Success

  val updatedMessage = stringResource(InspectionRes.string.inspection_updated)
  val deletedMessage = stringResource(InspectionRes.string.inspection_deleted)

  // Find the card in the list
  val card = successState?.allInspections?.find { it.id == viewModel.cardId }

  if (card != null && successState != null) {
    EditInspectionScreen(
      card = card,
      availableInspections = successState.allInspections,
      onCancel = { navController.popBackStack() },
      onSave = { updatedCard ->
        viewModel.saveEditedInspection(
          cardId = updatedCard.id,
          title = updatedCard.title,
          type = updatedCard.type,
          component = updatedCard.component,
          rules = updatedCard.rules,
          referenceNumber = updatedCard.reference_number,
          complianceAuthority = updatedCard.compliance_authority,
          complianceDetails = updatedCard.compliance_details,
          isOneTime = updatedCard.is_one_time,
          forceDueDate = updatedCard.force_due_date,
          forceDueEngine = updatedCard.force_due_engine_hour,
          notes = updatedCard.notes,
          onSuccess = {
            navController.previousBackStackEntry?.savedStateHandle?.set(
              "success_message",
              updatedMessage
            )
            navController.popBackStack()
          }
        )
      },
      onDeleteRequest = { id ->
        viewModel.deleteInspection(
          cardId = id,
          onSuccess = {
            navController.previousBackStackEntry?.savedStateHandle?.set(
              "success_message",
              deletedMessage
            )
            navController.popBackStack()
          }
        )
      }
    )
  }
}
