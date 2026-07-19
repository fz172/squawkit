package dev.fanfly.wingslog.feature.tasks.update.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.feature.attachment.model.visible
import dev.fanfly.wingslog.feature.attachment.viewing.AttachmentFormSection
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.TaskFormEvent
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.TaskUiState
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.TaskViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.attachment.sharedassets.generated.resources.file_read_error
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.task_added
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

@Composable
fun AddTaskRoute(
  navController: NavController,
  viewModel: TaskViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val formState by viewModel.formState.collectAsStateWithLifecycle()
  val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val showAttachmentPicker by viewModel.showAttachmentPicker.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  val attachmentUploadEnabled by viewModel.attachmentUploadEnabled.collectAsStateWithLifecycle()
  val successState = uiState as? TaskUiState.Success

  val successMessage = stringResource(Res.string.task_added)
  val fileReadErrorMessage = stringResource(AttachRes.string.file_read_error)
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is TaskFormEvent.PickError -> snackbarHostState.showSnackbar(
          fileReadErrorMessage
        )
      }
    }
  }

  val errorMessage = successState?.error?.asString()
  LaunchedEffect(errorMessage) {
    errorMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearError()
    }
  }

  if (successState != null) {
    AddTaskScreen(
      state = formState,
      availableInspections = successState.allInspections,
      onTitleChange = viewModel::onTitleChange,
      onComponentChange = viewModel::onComponentChange,
      onTypeChange = viewModel::onTypeChange,
      onScheduleChange = viewModel::onScheduleChange,
      onRefNumberChange = viewModel::onRefNumberChange,
      onComplianceAuthorityChange = viewModel::onComplianceAuthorityChange,
      onComplianceNotesChange = viewModel::onComplianceNotesChange,
      isSaving = isSaving,
      snackbarHostState = snackbarHostState,
      onCancel = { navController.popBackStack() },
      onSave = { card ->
        viewModel.saveNewTask(
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
          uploadEnabled = attachmentUploadEnabled,
          showPickerSheet = showAttachmentPicker,
          onAddClick = viewModel::showAttachmentPicker,
          onRemove = viewModel::removeAttachment,
          onPickFiles = viewModel::addLocalFiles,
          onAddLink = viewModel::addLink,
          onDismissSheet = viewModel::hideAttachmentPicker,
          onPickError = viewModel::onFilePickError,
        )
      },
    )
  }
}
