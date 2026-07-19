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
import dev.fanfly.wingslog.feature.tasks.update.compose.ScheduleState
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
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

/**
 * WIP values for the add/edit task form. Held in the ViewModel (not composable `remember`) so the
 * fields survive the form composables being torn down and re-created when the OS file picker
 * returns — see #254. The `initialX` baselines are captured on seed (edit) or construction (add)
 * to drive unsaved-changes detection.
 */
data class TaskFormState(
  val title: String = "",
  val component: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val type: ComplianceType = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  val schedule: ScheduleState = ScheduleState(),
  val refNumber: String = "",
  val complianceAuthority: String = "",
  val complianceNotes: String = "",
  val forceCompliedStatus: ForceCompliedStatus? = null,
  val forceOverrideEngine: Boolean = false,
  val forcedEngineHours: String = "",
  val forceOverrideDate: Boolean = false,
  val forcedDateMillis: Long? = null,
  val initialTitle: String = "",
  val initialComponent: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val initialType: ComplianceType = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  val initialSchedule: ScheduleState = ScheduleState(),
  val initialRefNumber: String = "",
  val initialComplianceAuthority: String = "",
  val initialComplianceNotes: String = "",
  val initialForceCompliedStatus: ForceCompliedStatus? = null,
  val initialForceOverrideEngine: Boolean = false,
  val initialForcedEngineHours: String = "",
  val initialForceOverrideDate: Boolean = false,
  val initialForcedDateMillis: Long? = null,
) {
  val hasChanges: Boolean
    get() = title != initialTitle ||
      component != initialComponent ||
      type != initialType ||
      schedule != initialSchedule ||
      refNumber != initialRefNumber ||
      complianceAuthority != initialComplianceAuthority ||
      complianceNotes != initialComplianceNotes ||
      forceCompliedStatus != initialForceCompliedStatus ||
      forceOverrideEngine != initialForceOverrideEngine ||
      (forceOverrideEngine && forcedEngineHours != initialForcedEngineHours) ||
      forceOverrideDate != initialForceOverrideDate ||
      (forceOverrideDate && forcedDateMillis != initialForcedDateMillis)

  companion object {
    fun fromTask(card: MaintenanceTask): TaskFormState {
      val schedule = ScheduleState.fromTask(card)
      val forceOverrideEngine = card.force_due_engine_hour > 0f
      val forcedEngineHours =
        if (forceOverrideEngine) card.force_due_engine_hour.toString() else ""
      val forceOverrideDate = card.force_due_date != null
      val forcedDateMillis = card.force_due_date?.let { it.getEpochSecond() * 1000 }
      return TaskFormState(
        title = card.title,
        component = card.component,
        type = card.type,
        schedule = schedule,
        refNumber = card.reference_number,
        complianceAuthority = card.compliance_authority,
        complianceNotes = card.compliance_details,
        forceCompliedStatus = card.force_complied_status,
        forceOverrideEngine = forceOverrideEngine,
        forcedEngineHours = forcedEngineHours,
        forceOverrideDate = forceOverrideDate,
        forcedDateMillis = forcedDateMillis,
        initialTitle = card.title,
        initialComponent = card.component,
        initialType = card.type,
        initialSchedule = schedule,
        initialRefNumber = card.reference_number,
        initialComplianceAuthority = card.compliance_authority,
        initialComplianceNotes = card.compliance_details,
        initialForceCompliedStatus = card.force_complied_status,
        initialForceOverrideEngine = forceOverrideEngine,
        initialForcedEngineHours = forcedEngineHours,
        initialForceOverrideDate = forceOverrideDate,
        initialForcedDateMillis = forcedDateMillis,
      )
    }
  }
}

class TaskViewModel(
  private val inspectionDataManager: TaskDataManager,
  private val attachmentManager: AttachmentManager,
  private val auth: FirebaseAuth,
  private val maintenanceLogManager: MaintenanceLogManager,
  private val featureLabManager: FeatureLabManager,
  private val sharingManager: SharingManager,
  private val taskDueManager: TaskDueManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String =
    checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  val cardId: String? = savedStateHandle[Screen.CARD_ID]

  private val _uiState = MutableStateFlow<TaskUiState>(TaskUiState.Loading)
  val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

  // WIP form values live here (not in composable `remember`) so they survive the form composables
  // being torn down and re-created when the OS file picker returns — see #254. `formSeeded` guards
  // the one-time seed from the loaded card so later task re-emissions don't clobber in-flight edits.
  private val _formState = MutableStateFlow(TaskFormState())
  val formState: StateFlow<TaskFormState> = _formState.asStateFlow()
  private var formSeeded = false

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
      combine(
        featureLabManager.observe(),
        sharingManager.observeHostedByOther(aircraftId),
      ) { flags, hostedByOther ->
        // Storage rules are user-scoped: a member cannot upload into the host's tree (design §9,
        // storage.rules). Offering an attach button on someone else's aircraft would produce a file
        // that silently never leaves the device.
        flags.attachmentUploadEnabled && !hostedByOther
      }
        .collect { enabled -> _attachmentUploadEnabled.value = enabled }
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
        // Pre-load attachments and seed the form once when editing.
        if (cardId != null) {
          cards.firstOrNull { it.id == cardId }?.let { card ->
            attachmentForm.seedIfEmpty(card.attachments)
            if (!formSeeded) {
              formSeeded = true
              _formState.value = TaskFormState.fromTask(card)
            }
          }
        }
      }
    }
  }

  // ── Form field changes ───────────────────────────────────────────────────

  fun onTitleChange(value: String) = _formState.update { it.copy(title = value) }

  fun onComponentChange(value: ComponentType) =
    _formState.update { it.copy(component = value) }

  fun onTypeChange(value: ComplianceType) = _formState.update { it.copy(type = value) }

  fun onScheduleChange(value: ScheduleState) =
    _formState.update { it.copy(schedule = value) }

  fun onRefNumberChange(value: String) = _formState.update { it.copy(refNumber = value) }

  fun onComplianceAuthorityChange(value: String) =
    _formState.update { it.copy(complianceAuthority = value) }

  fun onComplianceNotesChange(value: String) =
    _formState.update { it.copy(complianceNotes = value) }

  fun onForceOverrideEngineChange(value: Boolean) =
    _formState.update { it.copy(forceOverrideEngine = value) }

  fun onForcedEngineHoursChange(value: String) =
    _formState.update { it.copy(forcedEngineHours = value) }

  fun onForceOverrideDateChange(value: Boolean) =
    _formState.update { it.copy(forceOverrideDate = value) }

  fun onForcedDateMillisChange(value: Long?) =
    _formState.update { it.copy(forcedDateMillis = value) }

  fun onForceCompliedStatusChange(value: ForceCompliedStatus?) =
    _formState.update { it.copy(forceCompliedStatus = value) }

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
    viewModelScope.launch { attachmentForm.remove(id) }
  }

  override fun onCleared() {
    // If the form is closed without saving, reclaim any files that were added (and eagerly
    // uploaded) but never committed to a record.
    attachmentForm.discardUnsavedLocalBlobs()
    super.onCleared()
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
