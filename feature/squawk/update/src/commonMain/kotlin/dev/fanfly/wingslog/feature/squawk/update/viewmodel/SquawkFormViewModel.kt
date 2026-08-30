package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.DefectCreated
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentFormController
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.fanfly.wingslog.thing.SquawkPriority
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wingslog.feature.attachment.sharedassets.generated.resources.add_file_failed
import wingslog.feature.attachment.sharedassets.generated.resources.duplicate_file_skipped
import wingslog.feature.attachment.sharedassets.generated.resources.file_too_large
import wingslog.feature.attachment.sharedassets.generated.resources.files_over_limit_skipped
import kotlin.time.Clock
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

data class SquawkFormState(
  val thingId: String = "",
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
  data class NavigateToCreateLog(val thingId: String, val squawkId: String) :
    SquawkFormEvent

  data object PickError : SquawkFormEvent
}

class SquawkFormViewModel(
  private val squawkManager: SquawkManager,
  private val currentThingTemplate: CurrentThingTemplate,
  private val analytics: AnalyticsManager,
  private val attachmentManager: AttachmentManager,
  private val logManager: MaintenanceLogManager,
  private val auth: FirebaseAuth,
  private val subscriptionManager: SubscriptionManager,
  private val sharingManager: SharingManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val thingId: String =
    checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  private val squawkId: String? = savedStateHandle[Screen.SQUAWK_ID]

  private val _state = MutableStateFlow(
    SquawkFormState(
      thingId = thingId,
      squawkId = squawkId
    )
  )
  val state: StateFlow<SquawkFormState> = _state.asStateFlow()

  private val _events = Channel<SquawkFormEvent>()
  val events = _events.receiveAsFlow()

  private val attachmentForm =
    AttachmentFormController(attachmentManager, thingId)
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
      // The attachment gate is thing-scoped (§9.7): on a foreign host's thing the host pays and
      // the broker enforces the host's entitlement, so the member is never gated by their own
      // subscription; on an own thing the member's own entitlement governs. (Both default-open
      // until the subscription capability ships.)
      combine(
        subscriptionManager.canUploadAttachments(),
        sharingManager.observeIsForeignHosted(thingId),
      ) { canUpload, foreignHosted -> foreignHosted || canUpload }
        .collect { _attachmentUploadEnabled.value = it }
    }
  }

  private fun loadExisting(id: String) {
    viewModelScope.launch {
      squawkManager.observeSquawks(thingId)
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
      logManager.observeLogs(thingId)
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
        val message = error.toUiText()
        _state.update { it.copy(error = message) }
      }
    }
  }

  fun addLink(url: String, name: String) {
    attachmentForm.addLink(url, name)
  }

  fun removeAttachment(id: String) {
    viewModelScope.launch { attachmentForm.remove(id) }
  }

  override fun onCleared() {
    // If the form is closed without saving, reclaim any files that were added (and eagerly
    // uploaded) but never committed to a record.
    attachmentForm.discardUnsavedLocalBlobs()
    super.onCleared()
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
        else Clock.System.now()
          .toWireInstant(),
        attachments = attachments,
        addressed_by_log_id = current.addressedByLogId,
        dismiss_reason = current.dismissReason,
        dismissed_at = if (current.dismissedAtEpochSeconds > 0L)
          kotlin.time.Instant.fromEpochSeconds(current.dismissedAtEpochSeconds)
            .toWireInstant()
        else null,
      )
      val isNewSquawk = current.squawkId == null
      val result = if (isNewSquawk)
        squawkManager.addSquawk(thingId, squawk)
      else
        squawkManager.updateSquawk(thingId, squawk)

      _state.update { it.copy(isSaving = false) }
      result.onSuccess {
        if (isNewSquawk) {
          analytics.log(DefectCreated(templateId = currentThingTemplate.templateId))
        }
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
      squawkManager.reopenSquawk(thingId, squawkId)
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
      squawkManager.dismissSquawk(thingId, squawkId, reason)
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
      _events.send(SquawkFormEvent.NavigateToCreateLog(thingId, squawkId))
    }
  }

  fun onBack() {
    viewModelScope.launch { _events.send(SquawkFormEvent.NavigateBack) }
  }

}

/**
 * Maps every skip reason from [AttachmentFormController] to a message. Nothing the user picked is
 * dropped without a word — the pickers cannot cap multi-select or filter out files that are
 * already attached, so the form is where they find out.
 */
private fun AttachmentFormController.AddFileError.toUiText(): UiText =
  when (this) {
    AttachmentFormController.AddFileError.FileTooLarge ->
      UiText.StringRes(AttachRes.string.file_too_large)

    AttachmentFormController.AddFileError.Duplicate ->
      UiText.StringRes(AttachRes.string.duplicate_file_skipped)

    is AttachmentFormController.AddFileError.LimitExceeded ->
      UiText.StringRes(AttachRes.string.files_over_limit_skipped)

    is AttachmentFormController.AddFileError.Failed ->
      message?.let { UiText.DynamicString(it) }
        ?: UiText.StringRes(AttachRes.string.add_file_failed)
  }
