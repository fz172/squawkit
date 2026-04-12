package dev.fanfly.wingslog.feature.inspection.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.wire.Instant
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentManager
import dev.fanfly.wingslog.core.attachments.datamanager.PickedFile
import dev.fanfly.wingslog.core.attachments.datamanager.UploadState
import dev.fanfly.wingslog.core.attachments.model.PendingAttachment
import dev.fanfly.wingslog.core.attachments.model.fileCount
import dev.fanfly.wingslog.core.attachments.model.toLocalFile
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.inspection.datamanager.InspectionManager
import dev.fanfly.wingslog.feature.maintenance.datamanager.MaintenanceLogManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InspectionUiState {
  data object Loading : InspectionUiState
  data class Success(
    val aircraftId: String,
    val allInspections: List<InspectionCard> = emptyList(),
    val currentEngineHours: Float,
  ) : InspectionUiState
}

class InspectionViewModel(
  private val inspectionManager: InspectionManager,
  private val attachmentManager: AttachmentManager,
  private val auth: FirebaseAuth,
  private val maintenanceLogManager: MaintenanceLogManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String = checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  val cardId: String? = savedStateHandle[Screen.CARD_ID]

  private val _uiState = MutableStateFlow<InspectionUiState>(InspectionUiState.Loading)
  val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

  // Attachment state is kept separate so it survives inspection list reloads.
  private var saveJob: Job? = null
  private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
  val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

  private val _showAttachmentPicker = MutableStateFlow(false)
  val showAttachmentPicker: StateFlow<Boolean> = _showAttachmentPicker.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  val isSaving: StateFlow<Boolean> = combine(_isSaving, _pendingAttachments) { saving, list ->
    saving || list.any { it is PendingAttachment.Uploading }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true
  val filesAtLimit: Boolean get() = _pendingAttachments.value.fileCount() >= MAX_FILE_ATTACHMENTS

  init {
    loadData()
  }

  private fun loadData() {
    viewModelScope.launch {
      combine(
        inspectionManager.observeInspections(aircraftId),
        maintenanceLogManager.observeMaintenanceOverview(aircraftId)
      ) { cards, overview ->
        cards to overview
      }.collect { (cards, overview) ->
        val engineHours = overview?.current_engine_time?.toFloat() ?: 0f
        _uiState.update { InspectionUiState.Success(aircraftId, cards, engineHours) }
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
    val remaining = MAX_FILE_ATTACHMENTS - _pendingAttachments.value.fileCount()
    val toAdd = files
      .filter { it.sizeBytes <= MAX_FILE_SIZE_BYTES }
      .take(remaining)
      .map {
        PendingAttachment.LocalFile(
          generateRandomId(),
          it.name,
          it.uri,
          it.mimeType,
          it.sizeBytes
        )
      }
    _pendingAttachments.update { it + toAdd }
  }

  fun addLink(url: String, name: String) {
    val displayName = name.ifBlank { url.take(40) }
    _pendingAttachments.update {
      it + PendingAttachment.LocalLink(
        generateRandomId(),
        displayName,
        url
      )
    }
  }

  fun removeAttachment(id: String) {
    _pendingAttachments.update { list ->
      list.mapNotNull { pending ->
        when {
          pending.id != id -> pending
          pending is PendingAttachment.LocalFile -> null
          pending is PendingAttachment.LocalLink -> null
          pending is PendingAttachment.Failed -> null
          pending is PendingAttachment.Saved && pending.attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK -> null
          pending is PendingAttachment.Saved -> PendingAttachment.PendingDelete(pending.attachment)
          else -> pending
        }
      }
    }
  }

  fun cancelUpload(id: String) {
    saveJob?.cancel()
    saveJob = null
    _pendingAttachments.update { list ->
      list.map { if (it is PendingAttachment.Uploading && it.id == id) it.toLocalFile() else it }
    }
  }

  fun retryUpload(id: String) {
    _pendingAttachments.update { list ->
      list.map { if (it is PendingAttachment.Failed && it.id == id) it.toLocalFile() else it }
    }
  }

  // ── Save helpers ─────────────────────────────────────────────────────────

  /** Uploads local files and builds the final attachment list. Returns null on failure. */
  private suspend fun resolveAttachments(cardId: String): List<Attachment>? {
    val pending = _pendingAttachments.value
    val uploadedAttachments = mutableListOf<Attachment>()

    if (!isAnonymous) {
      for (pf in pending.filterIsInstance<PendingAttachment.LocalFile>()) {
        // Show this file as uploading in the UI
        _pendingAttachments.update { list ->
          list.map {
            if (it.id == pf.tempId) PendingAttachment.Uploading(
              pf.tempId,
              pf.name,
              mimeType = pf.mimeType,
              localUri = pf.localUri,
              sizeBytes = pf.sizeBytes
            )
            else it
          }
        }

        val storagePath =
          attachmentManager.buildInspectionCardPath(aircraftId, cardId, pf.tempId, pf.name)
            ?: return null
        var error: Throwable? = null
        attachmentManager.uploadFile(storagePath, pf.localUri, pf.mimeType, pf.name, pf.tempId)
          .collect { state ->
            when (state) {
              is UploadState.Uploading -> {
                if (state.progress > 0f) {
                  _pendingAttachments.update { list ->
                    list.map {
                      if (it.id == pf.tempId) PendingAttachment.Uploading(
                        pf.tempId,
                        pf.name,
                        state.progress,
                        pf.mimeType,
                        pf.localUri,
                        pf.sizeBytes
                      )
                      else it
                    }
                  }
                }
              }

              is UploadState.Done -> uploadedAttachments.add(state.attachment)
              is UploadState.Failed -> error = state.error
            }
          }
        if (error != null) {
          _pendingAttachments.update { list ->
            list.map {
              if (it is PendingAttachment.Uploading && it.id == pf.tempId)
                PendingAttachment.Failed(pf.tempId, pf.name, pf.mimeType, pf.localUri, pf.sizeBytes)
              else it
            }
          }
          coroutineScope { uploadedAttachments.forEach { launch { attachmentManager.deleteFile(it) } } }
          return null
        }
      }
      // Delete pending-delete files
      val toDelete = pending.filterIsInstance<PendingAttachment.PendingDelete>()
      if (toDelete.isNotEmpty()) {
        coroutineScope { toDelete.forEach { launch { attachmentManager.deleteFile(it.attachment) } } }
      }
    }

    val savedLinks = pending.filterIsInstance<PendingAttachment.LocalLink>().map { link ->
      Attachment(
        id = link.tempId, name = link.name,
        type = AttachmentType.ATTACHMENT_TYPE_LINK,
        url = link.url, storage_path = "", download_url = "", mime_type = "", size_bytes = 0L,
      )
    }
    val savedFiles = pending.filterIsInstance<PendingAttachment.Saved>().map { it.attachment }
    return savedFiles + uploadedAttachments + savedLinks
  }

  // ── Public save/delete ───────────────────────────────────────────────────

  fun saveNewInspection(
    title: String, type: ComplianceType, component: InspectionComponentType,
    rules: List<InspectionRule>, referenceNumber: String, complianceAuthority: String,
    complianceDetails: String, isOneTime: Boolean, forceDueDate: Instant?,
    forceDueEngine: Float, notes: String = "", onSuccess: () -> Unit, onError: () -> Unit = {},
  ) {
    saveJob = viewModelScope.launch {
      _isSaving.value = true
      try {
        val newCardId = generateRandomId()
        val attachments = resolveAttachments(newCardId) ?: run { onError(); return@launch }
        val card = InspectionCard(
          id = newCardId, title = title, type = type, component = component, rules = rules,
          reference_number = referenceNumber, compliance_authority = complianceAuthority,
          compliance_details = complianceDetails, is_one_time = isOneTime,
          force_due_date = forceDueDate, force_due_engine_hour = forceDueEngine,
          notes = notes, attachments = attachments,
        )
        inspectionManager.addInspection(aircraftId, card).onSuccess { onSuccess() }
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun saveEditedInspection(
    cardId: String,
    title: String,
    type: ComplianceType,
    component: InspectionComponentType,
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
        val attachments = resolveAttachments(cardId) ?: run { onError(); return@launch }
        val updatedCard = InspectionCard(
          id = cardId, title = title, type = type, component = component, rules = rules,
          reference_number = referenceNumber, compliance_authority = complianceAuthority,
          compliance_details = complianceDetails, is_one_time = isOneTime,
          force_due_date = forceDueDate, force_due_engine_hour = forceDueEngine,
          force_complied_status = forceCompliedStatus,
          notes = notes, attachments = attachments,
        )
        inspectionManager.updateInspection(aircraftId, updatedCard).onSuccess { onSuccess() }
      } finally {
        _isSaving.value = false
      }
    }
  }

  fun deleteInspection(cardId: String, onSuccess: () -> Unit) {
    viewModelScope.launch {
      // Best-effort: delete Storage files before removing the card
      if (!isAnonymous) {
        val fileAttachments = _pendingAttachments.value
          .filterIsInstance<PendingAttachment.Saved>()
          .filter { it.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK }
        coroutineScope { fileAttachments.forEach { launch { attachmentManager.deleteFile(it.attachment) } } }
      }
      inspectionManager.deleteInspection(aircraftId, cardId).onSuccess { onSuccess() }
    }
  }

  companion object {
    const val MAX_FILE_ATTACHMENTS = 3
    const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
  }
}
