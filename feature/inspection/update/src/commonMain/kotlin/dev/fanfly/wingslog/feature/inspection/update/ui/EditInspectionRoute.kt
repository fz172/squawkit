package dev.fanfly.wingslog.feature.inspection.update.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.attachments.model.visible
import dev.fanfly.wingslog.core.attachments.viewing.AttachmentFormSection
import dev.fanfly.wingslog.core.ui.common.navigation.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.feature.inspection.update.viewmodel.InspectionUiState
import dev.fanfly.wingslog.feature.inspection.update.viewmodel.InspectionViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes
import wingslog.feature.inspection.update.generated.resources.inspection_deleted
import wingslog.feature.inspection.update.generated.resources.inspection_updated

@Composable
fun EditInspectionRoute(
  navController: NavController,
  viewModel: InspectionViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val showAttachmentPicker by viewModel.showAttachmentPicker.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  val successState = uiState as? InspectionUiState.Success

  val updatedMessage = stringResource(InspectionRes.string.inspection_updated)
  val deletedMessage = stringResource(InspectionRes.string.inspection_deleted)

  // Find the card in the list
  val card = successState?.allInspections?.find { it.id == viewModel.cardId }

  if (card != null) {
    EditInspectionScreen(
      card = card,
      availableInspections = successState.allInspections,
      currentEngineHours = successState.currentEngineHours,
      isSaving = isSaving,
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
          forceCompliedStatus = updatedCard.force_complied_status,
          notes = updatedCard.notes,
          onSuccess = {
            navController.previousBackStackEntry?.savedStateHandle?.set(
              CROSS_SCREEN_SUCCESS_MESSAGE,
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
              CROSS_SCREEN_SUCCESS_MESSAGE,
              deletedMessage
            )
            navController.popBackStack()
          }
        )
      },
      attachmentSection = {
        AttachmentFormSection(
          visibleAttachments = pendingAttachments.visible(),
          isAnonymous = viewModel.isAnonymous,
          filesAtLimit = viewModel.filesAtLimit,
          showPickerSheet = showAttachmentPicker,
          onAddClick = viewModel::showAttachmentPicker,
          onRemove = viewModel::removeAttachment,
          onPickFiles = viewModel::addLocalFiles,
          onAddLink = viewModel::addLink,
          onDismissSheet = viewModel::hideAttachmentPicker,
          onCancelUpload = viewModel::cancelUpload,
          onRetryUpload = viewModel::retryUpload,
        )
      },
    )
  }
}
