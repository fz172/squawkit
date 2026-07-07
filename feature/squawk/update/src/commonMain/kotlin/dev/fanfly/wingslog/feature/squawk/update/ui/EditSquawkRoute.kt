package dev.fanfly.wingslog.feature.squawk.update.ui

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
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormEvent
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.SquawkFormViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.attachment.sharedassets.generated.resources.file_read_error
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_dismissed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_reopened
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_updated
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

@Composable
fun EditSquawkRoute(
  navController: NavController,
  viewModel: SquawkFormViewModel = koinViewModel(),
  attachmentsAvailable: Boolean = true,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val showAttachmentPicker by viewModel.showAttachmentPicker.collectAsStateWithLifecycle()
  val attachmentUploadEnabled by viewModel.attachmentUploadEnabled.collectAsStateWithLifecycle()
  val successMessage = stringResource(Res.string.squawk_updated)
  val dismissedMessage = stringResource(Res.string.squawk_dismissed)
  val reopenedMessage = stringResource(Res.string.squawk_reopened)
  val fileReadErrorMessage = stringResource(AttachRes.string.file_read_error)
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is SquawkFormEvent.NavigateBack -> navController.popBackStack()
        is SquawkFormEvent.SaveSuccess -> {
          navController.previousBackStackEntry?.savedStateHandle
            ?.set(CROSS_SCREEN_SUCCESS_MESSAGE, event.message)
          navController.popBackStack()
        }

        is SquawkFormEvent.PickError -> snackbarHostState.showSnackbar(
          fileReadErrorMessage
        )
      }
    }
  }

  val errorMessage = state.error?.asString()
  LaunchedEffect(errorMessage) {
    errorMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearError()
    }
  }

  SquawkFormScreen(
    state = state,
    snackbarHostState = snackbarHostState,
    onTitleChange = viewModel::onTitleChange,
    onDescriptionChange = viewModel::onDescriptionChange,
    onPriorityChange = viewModel::onPriorityChange,
    onSave = { viewModel.save(successMessage) },
    onBack = viewModel::onBack,
    onAddLog = viewModel::showLogPicker,
    onClearLog = viewModel::clearLog,
    onSelectLog = viewModel::selectLog,
    onHideLogPicker = viewModel::hideLogPicker,
    onDismissClick = viewModel::showDismissDialog,
    onDismissDialogDismiss = viewModel::hideDismissDialog,
    onDismissConfirm = { reason ->
      viewModel.confirmDismiss(
        reason,
        dismissedMessage
      )
    },
    onReopenClick = { viewModel.reopen(reopenedMessage) },
    attachmentSection = {
      if (attachmentsAvailable) {
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
      }
    },
  )
}
