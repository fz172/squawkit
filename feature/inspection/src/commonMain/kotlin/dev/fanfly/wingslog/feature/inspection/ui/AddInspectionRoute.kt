package dev.fanfly.wingslog.feature.inspection.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.inspection.generated.resources.inspection_added
import wingslog.feature.inspection.generated.resources.Res as InspectionRes

@Composable
fun AddInspectionRoute(
  navController: NavController,
  viewModel: InspectionViewModel = koinViewModel()
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val successState = uiState as? InspectionUiState.Success

  val successMessage = stringResource(InspectionRes.string.inspection_added)

  if (successState != null) {
    AddInspectionScreen(
      availableInspections = successState.allInspections,
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
          notes = card.notes,
          onSuccess = {
            navController.previousBackStackEntry?.savedStateHandle?.set(
              "success_message",
              successMessage
            )
            navController.popBackStack()
          }
        )
      }
    )
  }
}

