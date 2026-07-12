package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentFormController
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wingslog.feature.attachment.sharedassets.generated.resources.add_file_failed
import kotlin.time.Clock
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

data class SquawkFormState(
  val aircraftId: String = "",
  val squawkId: String? = null,
  val title: String = "",
  val description: String = "",
  val priority: SquawkPriority = SquawkPriority.SQUAWK_PRIORITY_LOW,
  val component: ComponentType = ComponentType.COMPONENT_UNKNOWN,
  val titleError: Boolean = false,
  val isSaving: Boolean = false,
  val isAddressedReadOnly: Boolean = false,
  val reportedDateFormatted: String = "",
  val createdAtEpochSeconds: Long = 0L,
  val addressedByLogId: String = "",
  val availableLogs: List<MaintenanceLog> = emptyList(),
  val showLogPicker: Boolean = false,
  val showResolveMenu: Boolean = false,
  val showDismissDialog: Boolean = false,
  val isDismissing: Boolean = false,
  val dismissReason: SquawkDismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
  val dismissedAtFormatted: String = "",
  val dismissedAtEpochSeconds: Long = 0L,
  // Baseline values captured on load — used to detect unsaved changes
  val initialTitle: String = "",
  val initialDescription: String = "",
  val initialPriority: SquawkPriority = SquawkPriority.SQUAWK_PRIORITY_LOW,
  val initialAddressedByLogId: String = "",
  val error: UiText? = null,
)

sealed interface SquawkFormEvent {
  data object NavigateBack : SquawkFormEvent
  data class SaveSuccess(val message: String) : SquawkFormEvent
  data class NavigateToCreateLog(val aircraftId: String, val squawkId: String) : SquawkFormEvent
  data object PickError : SquawkFormEvent
}

class SquawkFormViewModel(
  private val squawkManager: SquawkManager,
  private val attachmentManager: AttachmentManager,
  private val logManager: MaintenanceLogManager,
  private val auth: FirebaseAuth,
  private val featureLabManager: FeatureLabManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String =
    checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  private val squawkId: String? = savedStateHandle[Screen.SQUAWK_ID]

  private val _state = MutableStateFlow(
    SquawkFormState(
      aircraftId = aircraftId,
      squawkId = squawkId
    )
  )
  val state: StateFlow<SquawkFormState> = _state.asStateFlow()

  private val _events = Channel<SquawkFormEvent>()
  val events = _events.receiveAsFlow()

  private val attachmentForm =
    AttachmentFormController(attachmentManager, aircraftId)
  val pendingAttachments: StateFlow<List<PendingAttachment>> =
    attachmentForm.pendingAttachments
  val showAttachmentPicker: StateFlow<Boolean> = attachmentForm.showPicker

  private val _attachmentUploadEnabled = MutableStateFlow(false)
  val attachmentUploadEnabled: StateFlow<Boolean> =
    _attachmentUploadEnabled.asStateFlow()

  val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true
  val filesAtLimit: Boolean get() = attachmentForm.filesAtLimit

  init {
    if (squawkId != null) {
      loadExisting(squawkId)
      loadLogs()
    }
    viewModelScope.launch {
      featureLabManager.observe()
        .collect { flags ->
          _attachmentUploadEnabled.value = flags.attachmentUploadEnabled
        }
    }
  }

  private fun loadExisting(id: String) {
    viewModelScope.launch {
      squawkManager.observeSquawks(aircraftId)
        .collect { squawks ->
          val squawk = squawks.find { it.id == id } ?: return@collect
          _state.update {
            it.copy(
              title = squawk.title,
              description = squawk.description,
              priority = squawk.priority,
              component = squawk.component_type,
              isAddressedReadOnly = squawk.addressed_by_log_id.isNotEmpty(),
              reportedDateFormatted = squawk.created_at?.toLocalDate()
                ?.toDisplayFormat() ?: "",
              createdAtEpochSeconds = squawk.created_at?.getEpochSecond() ?: 0L,
              addressedByLogId = squawk.addressed_by_log_id,
              dismissReason = squawk.dismiss_reason,
              dismissedAtEpochSeconds = squawk.dismissed_at?.getEpochSecond()
                ?: 0L,
              dismissedAtFormatted = squawk.dismissed_at
                ?.takeIf { it.getEpochSecond() > 0L }
                ?.toLocalDate()
                ?.toDisplayFormat() ?: "",
              initialTitle = squawk.title,
              initialDescription = squawk.description,
              initialPriority = squawk.priority,
              initialAddressedByLogId = squawk.addressed_by_log_id,
            )
          }
          attachmentForm.seedIfEmpty(squawk.attachments)
        }
    }
  }

  private fun loadLogs() {
    viewModelScope.launch {
      logManager.observeLogs(aircraftId)
        .collect { logs ->
          _state.update { it.copy(availableLogs = logs) }
        }
    }
  }

  fun onTitleChange(value: String) =
    _state.update { it.copy(title = value, titleError = false) }

  fun onDescriptionChange(value: String) =
    _state.update { it.copy(description = value) }

  fun onPriorityChange(value: SquawkPriority) =
    _state.update { it.copy(priority = value) }

  fun onComponentChange(value: ComponentType) =
    _state.update { it.copy(component = value) }

  fun showLogPicker() = _state.update { it.copy(showLogPicker = true) }
  fun hideLogPicker() = _state.update { it.copy(showLogPicker = false) }
  fun selectLog(logId: String) =
    _state.update { it.copy(addressedByLogId = logId, showLogPicker = false) }

  fun clearLog() = _state.update { it.copy(addressedByLogId = "") }

  fun showAttachmentPicker() {
    attachmentForm.showPicker()
  }

  fun hideAttachmentPicker() {
    attachmentForm.hidePicker()
  }

  fun onFilePickError() {
    viewModelScope.launch { _events.send(SquawkFormEvent.PickError) }
  }

  fun clearError() = _state.update { it.copy(error = null) }

  fun addLocalFiles(files: List<PickedFile>) {
    viewModelScope.launch {
      attachmentForm.addLocalFiles(files) { error ->
        // Oversized files are skipped silently on this form; only surface hard failures.
        if (error is AttachmentFormController.AddFileError.Failed) {
          _state.update {
            it.copy(
              error = error.message?.let { msg -> UiText.DynamicString(msg) }
                ?: UiText.StringRes(AttachRes.string.add_file_failed)
            )
          }
        }
      }
    }
  }

  fun addLink(url: String, name: String) {
    attachmentForm.addLink(url, name)
  }

  fun removeAttachment(id: String) {
    attachmentForm.remove(id)
  }

  fun save(onSuccessMessage: String) {
    val current = _state.value
    if (current.title.isBlank()) {
      _state.update { it.copy(titleError = true) }
      return
    }
    _state.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      val resolvedId = current.squawkId ?: generateRandomId()
      val attachments = attachmentForm.resolveForSave()
      val squawk = Squawk(
        id = resolvedId,
        title = current.title.trim(),
        description = current.description.trim(),
        priority = current.priority,
        component_type = current.component,
        // updateSquawk writes the whole record, so an edit must carry the
        // original reported date forward or it is erased.
        created_at = if (current.createdAtEpochSeconds > 0L)
          kotlin.time.Instant.fromEpochSeconds(current.createdAtEpochSeconds)
            .toWireInstant()
        else Clock.System.now().toWireInstant(),
        attachments = attachments,
        addressed_by_log_id = current.addressedByLogId,
        dismiss_reason = current.dismissReason,
        dismissed_at = if (current.dismissedAtEpochSeconds > 0L)
          kotlin.time.Instant.fromEpochSeconds(current.dismissedAtEpochSeconds)
            .toWireInstant()
        else null,
      )
      val result = if (current.squawkId == null)
        squawkManager.addSquawk(aircraftId, squawk)
      else
        squawkManager.updateSquawk(aircraftId, squawk)

      _state.update { it.copy(isSaving = false) }
      result.onSuccess {
        _events.send(
          SquawkFormEvent.SaveSuccess(
            onSuccessMessage
          )
        )
      }
    }
  }

  fun reopen(onSuccessMessage: String) {
    val squawkId = _state.value.squawkId ?: return
    viewModelScope.launch {
      squawkManager.reopenSquawk(aircraftId, squawkId)
        .onSuccess { _events.send(SquawkFormEvent.SaveSuccess(onSuccessMessage)) }
    }
  }

  fun showResolveMenu() =
    _state.update { it.copy(showResolveMenu = true) }

  fun hideResolveMenu() =
    _state.update { it.copy(showResolveMenu = false) }

  fun selectDismissNoWorkPlanned() =
    _state.update { it.copy(showResolveMenu = false, showDismissDialog = true) }

  fun hideDismissDialog() = _state.update { it.copy(showDismissDialog = false) }

  fun confirmDismiss(reason: SquawkDismissReason, onSuccessMessage: String) {
    val squawkId = _state.value.squawkId ?: return
    _state.update { it.copy(showDismissDialog = false, isDismissing = true) }
    viewModelScope.launch {
      squawkManager.dismissSquawk(aircraftId, squawkId, reason)
        .onSuccess { _events.send(SquawkFormEvent.SaveSuccess(onSuccessMessage)) }
      _state.update { it.copy(isDismissing = false) }
    }
  }

  fun selectFixed() {
    val current = _state.value
    val squawkId = current.squawkId ?: return
    // Guards against a double-tap firing this twice before the menu's dismissal recomposes:
    // the first call flips showResolveMenu synchronously, so a second call sees it already false.
    if (!current.showResolveMenu) return
    _state.update { it.copy(showResolveMenu = false) }
    viewModelScope.launch {
      _events.send(SquawkFormEvent.NavigateToCreateLog(aircraftId, squawkId))
    }
  }

  fun onBack() {
    viewModelScope.launch { _events.send(SquawkFormEvent.NavigateBack) }
  }

}
