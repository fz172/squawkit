package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.fileCount
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
import kotlin.time.Clock

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
  val addressedByLogId: String = "",
  val availableLogs: List<MaintenanceLog> = emptyList(),
  val showLogPicker: Boolean = false,
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
)

sealed interface SquawkFormEvent {
  data object NavigateBack : SquawkFormEvent
  data class SaveSuccess(val message: String) : SquawkFormEvent
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

  private val _pendingAttachments =
    MutableStateFlow<List<PendingAttachment>>(emptyList())
  val pendingAttachments: StateFlow<List<PendingAttachment>> =
    _pendingAttachments.asStateFlow()

  private val _showAttachmentPicker = MutableStateFlow(false)
  val showAttachmentPicker: StateFlow<Boolean> =
    _showAttachmentPicker.asStateFlow()

  private val _attachmentUploadEnabled = MutableStateFlow(false)
  val attachmentUploadEnabled: StateFlow<Boolean> =
    _attachmentUploadEnabled.asStateFlow()

  val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true
  val filesAtLimit: Boolean get() = _pendingAttachments.value.fileCount() >= MAX_FILE_ATTACHMENTS

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
            if (_pendingAttachments.value.isEmpty()) {
              _pendingAttachments.value =
                squawk.attachments.map { PendingAttachment.Saved(it) }
            }
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
    _showAttachmentPicker.value = true
  }

  fun hideAttachmentPicker() {
    _showAttachmentPicker.value = false
  }

  fun addLocalFiles(files: List<PickedFile>) {
    viewModelScope.launch {
      for (file in files) {
        if (_pendingAttachments.value.fileCount() >= MAX_FILE_ATTACHMENTS) break
        if (file.sizeBytes > MAX_FILE_SIZE_BYTES) continue
        try {
          val attachment =
            attachmentManager.addPickedFile(aircraftId, file, file.name)
          _pendingAttachments.update { it + PendingAttachment.Local(attachment) }
        } catch (_: Exception) {
        }
      }
    }
  }

  fun addLink(url: String, name: String) {
    val displayName = name.ifBlank { url.take(40) }
    val attachment = attachmentManager.makeLink(url, displayName)
    _pendingAttachments.update { it + PendingAttachment.LocalLink(attachment) }
  }

  fun removeAttachment(id: String) {
    _pendingAttachments.update { list ->
      list.mapNotNull { pending ->
        when {
          pending.id != id -> pending
          pending is PendingAttachment.Local -> null
          pending is PendingAttachment.LocalLink -> null
          pending is PendingAttachment.Saved && pending.attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK -> null
          pending is PendingAttachment.Saved -> PendingAttachment.PendingDelete(
            pending.attachment
          )

          else -> pending
        }
      }
    }
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
      val attachments = resolveAttachments(resolvedId)
      val squawk = Squawk(
        id = resolvedId,
        title = current.title.trim(),
        description = current.description.trim(),
        priority = current.priority,
        component_type = current.component,
        created_at = if (current.squawkId == null) Clock.System.now()
            .toWireInstant() else null,
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

  fun showDismissDialog() = _state.update { it.copy(showDismissDialog = true) }
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

  fun onBack() {
    viewModelScope.launch { _events.send(SquawkFormEvent.NavigateBack) }
  }

  private suspend fun resolveAttachments(squawkId: String): List<Attachment> {
    val pending = _pendingAttachments.value
    pending.filterIsInstance<PendingAttachment.PendingDelete>()
        .forEach { attachmentManager.delete(it.attachment) }
    return buildList {
      addAll(
        pending.filterIsInstance<PendingAttachment.Saved>()
            .map { it.attachment })
      addAll(
        pending.filterIsInstance<PendingAttachment.Local>()
            .map { it.attachment })
      addAll(
        pending.filterIsInstance<PendingAttachment.LocalLink>()
            .map { it.attachment })
    }
  }

  companion object {
    const val MAX_FILE_ATTACHMENTS = 3
    const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
  }
}
