package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.wire.Instant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.currentFor
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentFormController
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.comments.model.CommentAction
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.comments.model.CommentThreadState
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.datamanager.forcedDueMeter
import dev.fanfly.wingslog.feature.tasks.datamanager.meterKeyFor
import dev.fanfly.wingslog.feature.tasks.datamanager.toDueDate
import dev.fanfly.wingslog.feature.tasks.datamanager.toPickerMillis
import dev.fanfly.wingslog.feature.tasks.datamanager.withForcedDueMeter
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.update.compose.ScheduleState
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.ForceCompliedStatus
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes
import wingslog.feature.attachment.sharedassets.generated.resources.add_file_failed
import wingslog.feature.attachment.sharedassets.generated.resources.duplicate_file_skipped
import wingslog.feature.attachment.sharedassets.generated.resources.file_too_large
import wingslog.feature.attachment.sharedassets.generated.resources.files_over_limit_skipped
import wingslog.feature.comments.sharedassets.generated.resources.Res as CommentsRes
import wingslog.feature.comments.sharedassets.generated.resources.comment_delete_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_edit_failed
import wingslog.feature.comments.sharedassets.generated.resources.comment_post_failed

sealed interface TaskUiState {
  data object Loading : TaskUiState
  data class Success(
    val thingId: String,
    val allInspections: List<MaintenanceTask> = emptyList(),
    val availableLogs: List<MaintenanceLog> = emptyList(),
    val currentEngineHours: Float,
    /** The latest reading of every meter the overview knows, by key — for the form's banner. */
    val currentReadings: Map<String, Float> = emptyMap(),
    val error: UiText? = null,
  ) : TaskUiState
}

sealed interface TaskFormEvent {
  data object PickError : TaskFormEvent
  data class NavigateToCreateLog(val thingId: String, val cardId: String) :
    TaskFormEvent
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
  val forceOverrideEngine: Boolean = false,
  val forcedEngineHours: String = "",
  val forceOverrideDate: Boolean = false,
  val forcedDateMillis: Long? = null,
  // "Resolve" popup off the bottom bar (Create Work Log / Skip This Cycle) — mirrors
  // SquawkFormState.showResolveMenu. Skip is an immediate-persist action (see
  // TaskViewModel.skipThisCycle), not a pending form field, so it isn't part of this form state.
  val showResolveMenu: Boolean = false,
  val initialTitle: String = "",
  val initialComponent: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val initialType: ComplianceType = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  val initialSchedule: ScheduleState = ScheduleState(),
  val initialRefNumber: String = "",
  val initialComplianceAuthority: String = "",
  val initialComplianceNotes: String = "",
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
      forceOverrideEngine != initialForceOverrideEngine ||
      (forceOverrideEngine && forcedEngineHours != initialForcedEngineHours) ||
      forceOverrideDate != initialForceOverrideDate ||
      (forceOverrideDate && forcedDateMillis != initialForcedDateMillis)

  companion object {
    fun fromTask(card: MaintenanceTask): TaskFormState {
      val schedule = ScheduleState.fromTask(card)
      // `force_due_meter` first, falling back to the legacy float — the override may have been
      // set by a build that predates the keyed field (#759).
      val forcedDue = card.forcedDueMeter()
      val forceOverrideEngine = forcedDue != null
      val forcedEngineHours = forcedDue?.second?.toString() ?: ""
      val forceOverrideDate = card.force_due_date != null
      val forcedDateMillis =
        card.force_due_date?.toDueDate()?.toPickerMillis()
      return TaskFormState(
        title = card.title,
        component = card.component,
        type = card.type,
        schedule = schedule,
        refNumber = card.reference_number,
        complianceAuthority = card.compliance_authority,
        complianceNotes = card.compliance_details,
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
  private val commentManager: CommentManager,
  private val auth: FirebaseAuth,
  private val maintenanceLogManager: MaintenanceLogManager,
  private val subscriptionManager: SubscriptionManager,
  private val sharingManager: SharingManager,
  private val taskDueManager: TaskDueManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val thingId: String =
    checkNotNull(savedStateHandle[Screen.THING_ID])
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
    AttachmentFormController(attachmentManager, thingId)
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

  /**
   * The comments thread, present only when editing: a task that has not been saved yet has no id
   * for a comment to point at. Null on the add form, where the tab is absent too.
   */
  val comments: CommentThreadController? = cardId?.let { id ->
    CommentThreadController(
      commentManager = commentManager,
      target = CommentTarget(thingId, id, CommentParentKind.MAINTENANCE_TASK),
      scope = viewModelScope,
    )
  }
  val commentState: StateFlow<CommentThreadState> =
    comments?.state ?: MutableStateFlow(CommentThreadState())
      .asStateFlow()

  val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true
  val filesAtLimit: Boolean get() = attachmentForm.filesAtLimit

  init {
    loadData()
    comments?.let { thread ->
      viewModelScope.launch {
        thread.errors.collect { action ->
          val message = when (action) {
            CommentAction.POST -> CommentsRes.string.comment_post_failed
            CommentAction.EDIT -> CommentsRes.string.comment_edit_failed
            CommentAction.DELETE -> CommentsRes.string.comment_delete_failed
          }
          _uiState.update { prev ->
            (prev as? TaskUiState.Success)?.copy(error = UiText.StringRes(message))
              ?: prev
          }
        }
      }
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

  private fun loadData() {
    viewModelScope.launch {
      combine(
        inspectionDataManager.observeTasks(thingId),
        maintenanceLogManager.observeLogs(thingId),
        maintenanceLogManager.observeMaintenanceOverview(thingId)
      ) { cards, logs, overview ->
        Triple(cards, logs, overview)
      }.collect { (cards, logs, overview) ->
        // By meter key, not by field name. `currentFor` falls back to the aviation field, so an
        // overview written before `current` existed still answers (#730).
        val engineHours =
          overview?.currentFor(MeterKeys.ENGINE_HOURS)
            ?.toFloat() ?: 0f
        _uiState.update { prev ->
          TaskUiState.Success(
            thingId = thingId,
            allInspections = cards,
            availableLogs = logs,
            currentEngineHours = engineHours,
            currentReadings = overview?.current.orEmpty()
              .associate { it.meter_key to it.value_.toFloat() },
            error = (prev as? TaskUiState.Success)?.error,
          )
        }
        // Pre-load attachments and seed the form once when editing.
        if (cardId != null) {
          cards.firstOrNull { it.id == cardId }
            ?.let { card ->
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

  /**
   * What [draft] — the form as it would be saved — is due against this Thing's real logs and
   * cards. The same computation the dashboard cards run, so the form's banner agrees with them
   * (#347); null before the Thing has loaded.
   */
  fun previewDue(draft: MaintenanceTask): DueMetadata? {
    val loaded = _uiState.value as? TaskUiState.Success ?: return null
    return taskDueManager.computeNextDue(draft, loaded.availableLogs, loaded.allInspections)
  }

  fun currentReading(meterKey: String): Float =
    (_uiState.value as? TaskUiState.Success)?.currentReadings?.get(meterKey) ?: 0f

  fun onTitleChange(value: String) =
    _formState.update { it.copy(title = value) }

  fun onComponentChange(value: ComponentType) =
    _formState.update { it.copy(component = value) }

  fun onTypeChange(value: ComplianceType) =
    _formState.update { it.copy(type = value) }

  fun onScheduleChange(value: ScheduleState) =
    _formState.update { it.copy(schedule = value) }

  fun onRefNumberChange(value: String) =
    _formState.update { it.copy(refNumber = value) }

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

  // ── Resolve menu (Create Work Log / Skip This Cycle) ─────────────────────

  fun showResolveMenu() = _formState.update { it.copy(showResolveMenu = true) }

  fun hideResolveMenu() = _formState.update { it.copy(showResolveMenu = false) }

  // Latched by selectCreateWorkLog so a double-tap can't queue two navigation events. Not part
  // of form state: the screen may raise an unsaved-changes prompt between the tap and this call,
  // so the open/closed state of the menu is no longer a usable guard.
  private var createWorkLogRequested = false

  fun selectCreateWorkLog() {
    val id = cardId ?: return
    if (createWorkLogRequested) return
    createWorkLogRequested = true
    _formState.update { it.copy(showResolveMenu = false) }
    viewModelScope.launch {
      _events.send(TaskFormEvent.NavigateToCreateLog(thingId, id))
    }
  }

  /**
   * Marks [card]'s current cycle complete without a log, persisting immediately against the
   * card as last saved (not any pending in-memory form edits) — mirrors
   * SquawkFormViewModel.confirmDismiss() calling squawkManager.dismissSquawk() directly.
   *
   * Clears any reschedule override as part of the same write, the way saving a linked
   * maintenance log does: TaskDueManager resolves force-due dates before it ever looks at
   * force-complied state, so a skip left alongside an override would never move the next due.
   */
  fun skipThisCycle(
    card: MaintenanceTask,
    currentEngineHours: Float,
    onSuccess: () -> Unit,
  ) {
    _formState.update { it.copy(showResolveMenu = false) }
    viewModelScope.launch {
      val skipped = card.withForcedDueMeter(card.defaultMeterKey(), null)
        .copy(
          force_due_date = null,
          force_complied_status = ForceCompliedStatus(
            complied_date = toWireInstant(Clock.System.now().epochSeconds),
            // Keyed, so a complied status records which meter it was measured in.
            complied_meter = MeterReading(
              meter_key = card.defaultMeterKey(),
              value_ = currentEngineHours.toDouble(),
            ),
          )
        )
      inspectionDataManager.updateTask(thingId, skipped)
        .onSuccess { onSuccess() }
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
        thingId,
        log.copy(inspection_ids = log.inspection_ids + taskId)
      )
      _showLogPicker.value = false
    }
  }

  fun removeLogFromHistory(taskId: String, log: MaintenanceLog) {
    viewModelScope.launch {
      maintenanceLogManager.updateLog(
        thingId,
        log.copy(inspection_ids = log.inspection_ids - taskId)
      )
    }
  }

  fun addLocalFiles(files: List<PickedFile>) {
    viewModelScope.launch {
      attachmentForm.addLocalFiles(files) { error ->
        val message = error.toUiText()
        _uiState.update { state ->
          if (state is TaskUiState.Success) state.copy(error = message) else state
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
          notes = notes,
          attachments = attachments,
        ).withForcedDueMeter(
          // The meter the rules schedule against — an override is measured in the same one.
          meterKeyFor(component, rules),
          forceDueEngine.takeIf { it > 0f },
        )
        inspectionDataManager.addTask(
          thingId,
          card
        )
          .onSuccess { onSuccess() }
      } finally {
        _isSaving.value = false
      }
    }
  }

  /**
   * True when this save changes the schedule the stored card's force-complied status was
   * recorded against.
   *
   * A skip ("Skip This Cycle") marks one specific cycle complete. Once the rules or the
   * reschedule override move, that cycle no longer describes anything real, and leaving the
   * status in place would silently advance a due date the user never skipped — so the caller
   * drops it. Unknown cards (not yet loaded) are treated as unchanged: better to carry the
   * status forward than to clear one we can't compare against.
   */
  private fun isScheduleChanged(
    cardId: String,
    rules: List<InspectionRule>,
    isOneTime: Boolean,
    forceDueDate: Instant?,
    forceDueEngine: Float,
  ): Boolean {
    val stored = (_uiState.value as? TaskUiState.Success)
      ?.allInspections
      ?.find { it.id == cardId }
      ?: return false
    return rules != stored.rules ||
      isOneTime != stored.is_one_time ||
      forceDueDate != stored.force_due_date ||
      forceDueEngine != (stored.forcedDueMeter()?.second ?: 0f)
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
          force_complied_status = if (
            isScheduleChanged(
              cardId,
              rules,
              isOneTime,
              forceDueDate,
              forceDueEngine
            )
          ) null else forceCompliedStatus,
          notes = notes,
          attachments = attachments,
        )
        inspectionDataManager.updateTask(
          thingId,
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
        thingId,
        cardId
      )
        .onSuccess { onSuccess() }
    }
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
