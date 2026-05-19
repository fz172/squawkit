package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.wire.Instant
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.fileCount
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TaskUiState {
  data object Loading : TaskUiState
  data class Success(
    val aircraftId: String,
    val allInspections: List<MaintenanceTask> = emptyList(),
    val currentEngineHours: Float,
    val naturalDueMetadata: DueMetadata? = null,
  ) : TaskUiState
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

  private val aircraftId: String = checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  val cardId: String? = savedStateHandle[Screen.CARD_ID]

  private val _uiState = MutableStateFlow<TaskUiState>(TaskUiState.Loading)
  val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

  // Attachment state is kept separate so it survives inspection list reloads.
  private var saveJob: Job? = null
  private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
  val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

  private val _showAttachmentPicker = MutableStateFlow(false)
  val showAttachmentPicker: StateFlow<Boolean> = _showAttachmentPicker.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

  private val _attachmentUploadEnabled = MutableStateFlow(true)
  val attachmentUploadEnabled: StateFlow<Boolean> = _attachmentUploadEnabled.asStateFlow()

  val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true
  val filesAtLimit: Boolean get() = _pendingAttachments.value.fileCount() >= MAX_FILE_ATTACHMENTS

  init {
    loadData()
    viewModelScope.launch {
      featureLabManager.observe().collect { flags ->
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
          cards.firstOrNull { it.id == id }?.let { card ->
            val stripped = card.copy(
              force_complied_status = null,
              force_due_date = null,
              force_due_engine_hour = 0f,
            )
            taskDueManager.computeNextDue(stripped, logs, cards)
          }
        }
        _uiState.update {
          TaskUiState.Success(
            aircraftId,
            cards,
            engineHours,
            naturalDue,
          )
        }
        // Pre-load attachments when editing
        if (cardId != null && _pendingAttachments.value.isEmpty()) {
          cards.firstOrNull { it.id == cardId }?.let { card ->
            _pendingAttachments.value = card.attachments.map { PendingAttachment.Saved(it) }
          }
        }
      }
    }
  }

  // ── Attachment management ────────────────────────────────────────────────

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
          val attachment = attachmentManager.addPickedFile(
            aircraftId,
            file,
            file.name
          )
          _pendingAttachments.update { it + PendingAttachment.Local(attachment) }
        } catch (_: Exception) {
          // Individual file errors are surfaced via per-attachment status; skip
        }
      }
    }
  }

  fun addLink(
    url: String,
    name: String,
  ) {
    val displayName = name.ifBlank { url.take(40) }
    val attachment = attachmentManager.makeLink(
      url,
      displayName
    )
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
          pending is PendingAttachment.Saved -> PendingAttachment.PendingDelete(pending.attachment)
          else -> pending
        }
      }
    }
  }

  // ── Save helpers ─────────────────────────────────────────────────────────

  /** Tombstones deleted attachments and builds the final attachment list. */
  private suspend fun resolveAttachments(cardId: String): List<Attachment> {
    val pending = _pendingAttachments.value
    pending.filterIsInstance<PendingAttachment.PendingDelete>()
      .forEach { attachmentManager.delete(it.attachment) }
    return buildList {
      addAll(pending.filterIsInstance<PendingAttachment.Saved>().map { it.attachment })
      addAll(pending.filterIsInstance<PendingAttachment.Local>().map { it.attachment })
      addAll(pending.filterIsInstance<PendingAttachment.LocalLink>().map { it.attachment })
    }
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
        val attachments = resolveAttachments(newCardId)
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
        ).onSuccess { onSuccess() }
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
        val attachments = resolveAttachments(cardId)
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
        ).onSuccess { onSuccess() }
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
      _pendingAttachments.value
        .filterIsInstance<PendingAttachment.Saved>()
        .filter { it.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK }
        .forEach { attachmentManager.delete(it.attachment) }
      inspectionDataManager.deleteTask(
        aircraftId,
        cardId
      ).onSuccess { onSuccess() }
    }
  }

  companion object {
    const val MAX_FILE_ATTACHMENTS = 3
    const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
  }
}
