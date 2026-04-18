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
import wingslog.feature.inspection.update.generated.resources.task_added

@Composable
fun AddInspectionRoute(
  navController: NavController,
  viewModel: InspectionViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val showAttachmentPicker by viewModel.showAttachmentPicker.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  val successState = uiState as? InspectionUiState.Success

  val successMessage = stringResource(InspectionRes.string.task_added)

  if (successState != null) {
    AddInspectionScreen(
      availableInspections = successState.allInspections,
      isSaving = isSaving,
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
              CROSS_SCREEN_SUCCESS_MESSAGE,
              successMessage
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
