package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.wire.Instant
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentFormController
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wingslog.feature.attachment.sharedassets.generated.resources.add_file_failed
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

sealed interface TaskUiState {
  data object Loading : TaskUiState
  data class Success(
    val aircraftId: String,
    val allInspections: List<MaintenanceTask> = emptyList(),
    val availableLogs: List<MaintenanceLog> = emptyList(),
    val currentEngineHours: Float,
    val naturalDueMetadata: DueMetadata? = null,
    val error: UiText? = null,
  ) : TaskUiState
}

sealed interface TaskFormEvent {
  data object PickError : TaskFormEvent
}

class TaskViewModel(
  private val inspectionDataManager: TaskDataManager,
  private val attachmentManager: AttachmentManager,
  private val auth: FirebaseAuth,
  private val maintenanceLogManager: MaintenanceLogManager,
  private val featureLabManager: FeatureLabManager,
  private val taskDueManager: TaskDueManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String =
    checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  val cardId: String? = savedStateHandle[Screen.CARD_ID]

  private val _uiState = MutableStateFlow<TaskUiState>(TaskUiState.Loading)
  val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

  private val _events = Channel<TaskFormEvent>()
  val events = _events.receiveAsFlow()

  // Attachment state is kept separate so it survives inspection list reloads.
  private var saveJob: Job? = null
  private val attachmentForm =
    AttachmentFormController(attachmentManager, aircraftId)
  val pendingAttachments: StateFlow<List<PendingAttachment>> =
    attachmentForm.pendingAttachments
  val showAttachmentPicker: StateFlow<Boolean> = attachmentForm.showPicker

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _showLogPicker = MutableStateFlow(false)
  val showLogPicker: StateFlow<Boolean> = _showLogPicker.asStateFlow()

  private val _attachmentUploadEnabled = MutableStateFlow(false)
  val attachmentUploadEnabled: StateFlow<Boolean> =
    _attachmentUploadEnabled.asStateFlow()

  val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true
  val filesAtLimit: Boolean get() = attachmentForm.filesAtLimit

  init {
    loadData()
    viewModelScope.launch {
      featureLabManager.observe()
        .collect { flags ->
          _attachmentUploadEnabled.value = flags.attachmentUploadEnabled
        }
    }
  }

  private fun loadData() {
    viewModelScope.launch {
      combine(
        inspectionDataManager.observeTasks(aircraftId),
        maintenanceLogManager.observeLogs(aircraftId),
        maintenanceLogManager.observeMaintenanceOverview(aircraftId)
      ) { cards, logs, overview ->
        Triple(cards, logs, overview)
      }.collect { (cards, logs, overview) ->
        val engineHours = overview?.current_engine_time?.toFloat() ?: 0f
        // Compute the rules-only "natural" next-due for the card being edited so the
        // adjustments preview banner can show what the schedule would say absent any
        // force-override or force-complied state.
        val naturalDue = cardId?.let { id ->
          cards.firstOrNull { it.id == id }
            ?.let { card ->
              val stripped = card.copy(
                force_complied_status = null,
                force_due_date = null,
                force_due_engine_hour = 0f,
              )
              taskDueManager.computeNextDue(stripped, logs, cards)
            }
        }
        _uiState.update { prev ->
          TaskUiState.Success(
            aircraftId = aircraftId,
            allInspections = cards,
            availableLogs = logs,
            currentEngineHours = engineHours,
            naturalDueMetadata = naturalDue,
            error = (prev as? TaskUiState.Success)?.error,
          )
        }
        // Pre-load attachments when editing
        if (cardId != null) {
          cards.firstOrNull { it.id == cardId }
            ?.let { card -> attachmentForm.seedIfEmpty(card.attachments) }
        }
      }
    }
  }

  // ── Attachment management ────────────────────────────────────────────────

  fun showAttachmentPicker() {
    attachmentForm.showPicker()
  }

  fun hideAttachmentPicker() {
    attachmentForm.hidePicker()
  }

  fun showLogPicker() {
    _showLogPicker.value = true
  }

  fun hideLogPicker() {
    _showLogPicker.value = false
  }

  fun onFilePickError() {
    viewModelScope.launch { _events.send(TaskFormEvent.PickError) }
  }

  fun clearError() {
    _uiState.update { state ->
      if (state is TaskUiState.Success) state.copy(
        error = null
      ) else state
    }
  }

  fun addLogToHistory(taskId: String, log: MaintenanceLog) {
    if (taskId in log.inspection_ids) return
    viewModelScope.launch {
      maintenanceLogManager.updateLog(
        aircraftId,
        log.copy(inspection_ids = log.inspection_ids + taskId)
      )
      _showLogPicker.value = false
    }
  }

  fun removeLogFromHistory(taskId: String, log: MaintenanceLog) {
    viewModelScope.launch {
      maintenanceLogManager.updateLog(
        aircraftId,
        log.copy(inspection_ids = log.inspection_ids - taskId)
      )
    }
  }

  fun addLocalFiles(files: List<PickedFile>) {
    viewModelScope.launch {
      attachmentForm.addLocalFiles(files) { error ->
        // Oversized files are skipped silently on this form; only surface hard failures.
        if (error is AttachmentFormController.AddFileError.Failed) {
          _uiState.update { state ->
            if (state is TaskUiState.Success) {
              state.copy(
                error = error.message?.let { msg -> UiText.DynamicString(msg) }
                  ?: UiText.StringRes(AttachRes.string.add_file_failed)
              )
            } else state
          }
        }
      }
    }
  }

  fun addLink(
    url: String,
    name: String,
  ) {
    attachmentForm.addLink(url, name)
  }

  fun removeAttachment(id: String) {
    attachmentForm.remove(id)
  }

  // ── Public save/delete ───────────────────────────────────────────────────

  fun saveNewTask(
    title: String,
    type: ComplianceType,
    component: ComponentType,
    rules: List<InspectionRule>,
    referenceNumber: String,
    complianceAuthority: String,
    complianceDetails: String,
    isOneTime: Boolean,
    forceDueDate: Instant?,
    forceDueEngine: Float,
    notes: String = "",
    onSuccess: () -> Unit,
    onError: () -> Unit = {},
  ) {
    saveJob = viewModelScope.launch {
      _isSaving.value = true
      try {
        val newCardId = generateRandomId()
        val attachments = attachmentForm.resolveForSave()
        val card = MaintenanceTask(
          id = newCardId,
          title = title,
          type = type,
          component = component,
          rules = rules,
          reference_number = referenceNumber,
          compliance_authority = complianceAuthority,
          compliance_details = complianceDetails,
          is_one_time = isOneTime,
          force_due_date = forceDueDate,
          force_due_engine_hour = forceDueEngine,
          notes = notes,
          attachments = attachments,
        )
        inspectionDataManager.addTask(
          aircraftId,
          card
        )
          .onSuccess { onSuccess() }
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun saveEditedTask(
    cardId: String,
    title: String,
    type: ComplianceType,
    component: ComponentType,
    rules: List<InspectionRule>,
    referenceNumber: String,
    complianceAuthority: String,
    complianceDetails: String,
    isOneTime: Boolean,
    forceDueDate: Instant?,
    forceDueEngine: Float,
    forceCompliedStatus: ForceCompliedStatus?,
    notes: String,
    onSuccess: () -> Unit,
    onError: () -> Unit = {},
  ) {
    saveJob = viewModelScope.launch {
      _isSaving.value = true
      try {
        val attachments = attachmentForm.resolveForSave()
        val updatedCard = MaintenanceTask(
          id = cardId,
          title = title,
          type = type,
          component = component,
          rules = rules,
          reference_number = referenceNumber,
          compliance_authority = complianceAuthority,
          compliance_details = complianceDetails,
          is_one_time = isOneTime,
          force_due_date = forceDueDate,
          force_due_engine_hour = forceDueEngine,
          force_complied_status = forceCompliedStatus,
          notes = notes,
          attachments = attachments,
        )
        inspectionDataManager.updateTask(
          aircraftId,
          updatedCard
        )
          .onSuccess { onSuccess() }
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun deleteTask(
    cardId: String,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      attachmentForm.deleteSavedFiles()
      inspectionDataManager.deleteTask(
        aircraftId,
        cardId
      )
        .onSuccess { onSuccess() }
    }
  }
}
