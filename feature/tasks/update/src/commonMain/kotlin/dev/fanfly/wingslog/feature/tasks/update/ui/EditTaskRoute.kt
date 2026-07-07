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
import wingslog.feature.tasks.update.generated.resources.task_deleted
import wingslog.feature.tasks.update.generated.resources.task_updated
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

@Composable
fun EditTaskRoute(
  navController: NavController,
  viewModel: TaskViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val showAttachmentPicker by viewModel.showAttachmentPicker.collectAsStateWithLifecycle()
  val showLogPicker by viewModel.showLogPicker.collectAsStateWithLifecycle()
  val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
  val attachmentUploadEnabled by viewModel.attachmentUploadEnabled.collectAsStateWithLifecycle()
  val successState = uiState as? TaskUiState.Success

  val updatedMessage = stringResource(Res.string.task_updated)
  val deletedMessage = stringResource(Res.string.task_deleted)
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

  // Find the card in the list
  val card = successState?.allInspections?.find { it.id == viewModel.cardId }

  if (card != null) {
    EditTaskScreen(
      card = card,
      availableInspections = successState.allInspections,
      availableLogs = successState.availableLogs,
      currentEngineHours = successState.currentEngineHours,
      naturalDueMetadata = successState.naturalDueMetadata,
      isSaving = isSaving,
      snackbarHostState = snackbarHostState,
      showLogPicker = showLogPicker,
      onShowLogPicker = viewModel::showLogPicker,
      onDismissLogPicker = viewModel::hideLogPicker,
      onAddLog = { log ->
        viewModel.cardId?.let {
          viewModel.addLogToHistory(
            it,
            log
          )
        }
      },
      onRemoveLog = { log ->
        viewModel.cardId?.let {
          viewModel.removeLogFromHistory(
            it,
            log
          )
        }
      },
      onCancel = { navController.popBackStack() },
      onSave = { updatedCard ->
        viewModel.saveEditedTask(
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
        viewModel.deleteTask(
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
