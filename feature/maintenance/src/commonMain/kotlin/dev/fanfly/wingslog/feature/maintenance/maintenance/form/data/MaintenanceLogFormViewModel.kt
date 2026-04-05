package dev.fanfly.wingslog.feature.maintenance.maintenance.form.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentManager
import dev.fanfly.wingslog.core.attachments.datamanager.PickedFile
import dev.fanfly.wingslog.core.attachments.datamanager.UploadState
import dev.fanfly.wingslog.core.attachments.model.PendingAttachment
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.inspection.datamanager.InspectionManager
import dev.fanfly.wingslog.feature.maintenance.database.AircraftManager
import dev.fanfly.wingslog.feature.maintenance.database.MaintenanceLogManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import wingslog.core.attachments.sharedassets.generated.resources.Res as AttachmentRes
import wingslog.core.attachments.sharedassets.generated.resources.file_too_large
import wingslog.core.attachments.sharedassets.generated.resources.upload_failed
import wingslog.core.attachments.sharedassets.generated.resources.upload_network_error
import wingslog.core.attachments.sharedassets.generated.resources.upload_permission_error
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.delete_failed
import wingslog.core.ui.generated.resources.save_failed
import wingslog.feature.maintenance.generated.resources.Res as MaintenanceRes
import wingslog.feature.maintenance.generated.resources.log_not_found
import wingslog.feature.maintenance.generated.resources.work_description_required

class MaintenanceLogFormViewModel(
  private val logManager: MaintenanceLogManager,
  private val aircraftManager: AircraftManager,
  private val inspectionManager: InspectionManager,
  private val attachmentManager: AttachmentManager,
  private val auth: FirebaseAuth,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])
  private val logId: String? = savedStateHandle["logId"]
  val isEditMode: Boolean get() = logId != null

  private val _uiState = MutableStateFlow(MaintenanceLogFormUiState())
  val uiState: StateFlow<MaintenanceLogFormUiState> = _uiState.asStateFlow()

  private val _events = Channel<MaintenanceLogFormEvent>()
  val events = _events.receiveAsFlow()

  init {
    loadAircraft()
    observeInspections()
    checkAuth()
    if (isEditMode) loadLog()
  }

  private fun checkAuth() {
    val user = auth.currentUser
    _uiState.update { it.copy(isAnonymous = user == null || user.isAnonymous) }
  }

  private fun observeInspections() {
    inspectionManager.observeInspections(aircraftId)
      .onEach { cards ->
        _uiState.update { it.copy(availableInspectionCards = cards) }
      }
      .launchIn(viewModelScope)
  }

  private fun loadAircraft() {
    viewModelScope.launch {
      aircraftManager.loadAircraft(aircraftId).collect { aircraft ->
        _uiState.update { it.copy(aircraft = aircraft) }
      }
    }
  }

  private fun loadLog() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val log = logManager.observeLogs(aircraftId)
        .firstOrNull()
        ?.firstOrNull { it.id == logId }
      if (log != null) {
        val logDate = log.timestamp?.let { ts ->
          val epochSec = ts.getEpochSecond()
          if (epochSec > 0L) {
            Instant.fromEpochSeconds(epochSec, ts.getNano())
              .toLocalDateTime(TimeZone.currentSystemDefault()).date
          } else null
        }
        val existingAttachments = log.attachments.map { PendingAttachment.Saved(it) }
        _uiState.update {
          it.copy(
            isLoading = false,
            workDescription = log.work_description,
            selectedInspectionIds = log.inspection_ids,
            engineTime = if (log.engine_hour > 0.0) log.engine_hour.toString() else "",
            airframeTime = if (log.airframe_time > 0.0) log.airframe_time.toString() else "",
            propTime = if (log.prop_time > 0.0) log.prop_time.toString() else "",
            selectedComponentType = log.component_type,
            selectedSubComponent = log.component_serial.ifEmpty { null },
            maintenanceDate = logDate,
            pendingAttachments = existingAttachments,
          )
        }
      } else {
        _uiState.update {
          it.copy(
            isLoading = false,
            error = UiText.StringRes(MaintenanceRes.string.log_not_found)
          )
        }
      }
    }
  }

  // ── Inspection ─────────────────────────────────────────────────────────────

  fun onMaintenanceDateChange(date: LocalDate?) =
    _uiState.update { it.copy(maintenanceDate = date) }

  fun onWorkDescriptionChange(value: String) = _uiState.update { it.copy(workDescription = value) }
  fun onInspectionIdsChange(value: List<String>) =
    _uiState.update { it.copy(selectedInspectionIds = value) }

  fun showInspectionPicker() = _uiState.update { it.copy(showInspectionPicker = true) }
  fun hideInspectionPicker() = _uiState.update { it.copy(showInspectionPicker = false) }
  fun toggleInspectionSelection(cardId: String) {
    _uiState.update { state ->
      val current = state.selectedInspectionIds.toMutableList()
      if (cardId in current) current.remove(cardId) else current.add(cardId)
      state.copy(selectedInspectionIds = current)
    }
  }

  fun removeInspectionId(cardId: String) {
    _uiState.update { state ->
      state.copy(selectedInspectionIds = state.selectedInspectionIds.filter { it != cardId })
    }
  }

  fun onEngineTimeChange(value: String) = _uiState.update { it.copy(engineTime = value) }
  fun onAirframeTimeChange(value: String) = _uiState.update { it.copy(airframeTime = value) }
  fun onPropTimeChange(value: String) = _uiState.update { it.copy(propTime = value) }
  fun onComponentTypeChange(value: MaintenanceLog.ComponentType) {
    _uiState.update { state ->
      val autoSerial = if (value == MaintenanceLog.ComponentType.AIRFRAME) {
        state.aircraft?.serial?.takeIf { it.isNotEmpty() }
      } else null
      state.copy(selectedComponentType = value, selectedSubComponent = autoSerial)
    }
  }

  fun onSubComponentChange(serial: String?) =
    _uiState.update { it.copy(selectedSubComponent = serial) }

  // ── Attachments ─────────────────────────────────────────────────────────────

  fun showAttachmentPicker() = _uiState.update { it.copy(showAttachmentPicker = true) }
  fun hideAttachmentPicker() = _uiState.update { it.copy(showAttachmentPicker = false) }

  fun onFilePickError() {
    viewModelScope.launch { _events.send(MaintenanceLogFormEvent.PickError) }
  }

  fun addLocalFiles(files: List<PickedFile>) {
    var anyAdded = false
    _uiState.update { state ->
      val remaining = MaintenanceLogFormUiState.MAX_FILE_ATTACHMENTS - state.fileAttachmentCount
      val toAdd = files.filter { it.sizeBytes <= MaintenanceLogFormUiState.MAX_FILE_SIZE_BYTES }
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
      val oversized = files.any { it.sizeBytes > MaintenanceLogFormUiState.MAX_FILE_SIZE_BYTES }
      anyAdded = toAdd.isNotEmpty()
      state.copy(
        pendingAttachments = state.pendingAttachments + toAdd,
        error = if (oversized) UiText.StringRes(AttachmentRes.string.file_too_large) else state.error,
      )
    }
    if (anyAdded) viewModelScope.launch { _events.send(MaintenanceLogFormEvent.FileAdded) }
  }

  fun addLink(url: String, name: String) {
    val displayName = name.ifBlank { url.take(40) }
    _uiState.update { state ->
      val link = PendingAttachment.LocalLink(generateRandomId(), displayName, url)
      state.copy(pendingAttachments = state.pendingAttachments + link)
    }
    viewModelScope.launch { _events.send(MaintenanceLogFormEvent.LinkAdded) }
  }

  fun removeAttachment(id: String) {
    _uiState.update { state ->
      val updated = state.pendingAttachments.mapNotNull { pending ->
        when {
          pending.id != id -> pending
          pending is PendingAttachment.LocalFile -> null
          pending is PendingAttachment.LocalLink -> null
          pending is PendingAttachment.Saved && pending.attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK -> null
          pending is PendingAttachment.Saved -> PendingAttachment.PendingDelete(pending.attachment)
          else -> pending
        }
      }
      state.copy(pendingAttachments = updated)
    }
  }

  // ── Save ────────────────────────────────────────────────────────────────────

  fun save() {
    val state = _uiState.value
    if (state.workDescription.isBlank()) {
      _uiState.update { it.copy(error = UiText.StringRes(MaintenanceRes.string.work_description_required)) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, error = null) }

      val resolvedLogId = logId ?: generateRandomId()

      // 1. Upload local file attachments (skip for anonymous users)
      val uploadedAttachments = mutableListOf<Attachment>()
      if (!state.isAnonymous) {
        val localFiles = state.pendingAttachments.filterIsInstance<PendingAttachment.LocalFile>()
        for (pending in localFiles) {
          val storagePath = attachmentManager.buildMaintenanceLogPath(
            aircraftId, resolvedLogId, pending.tempId, pending.name
          )
          if (storagePath == null) {
            _uiState.update {
              it.copy(
                isSaving = false,
                error = UiText.StringRes(CoreRes.string.save_failed)
              )
            }
            return@launch
          }
          var uploadError: Throwable? = null
          attachmentManager.uploadFile(
            storagePath,
            pending.localUri,
            pending.mimeType,
            pending.name,
            pending.tempId
          )
            .collect { uploadState ->
              when (uploadState) {
                is UploadState.Done -> uploadedAttachments.add(uploadState.attachment)
                is UploadState.Failed -> uploadError = uploadState.error
                else -> {}
              }
            }
          if (uploadError != null) {
            // Rollback any files uploaded so far in this batch
            coroutineScope { uploadedAttachments.forEach { launch { attachmentManager.deleteFile(it) } } }
            _uiState.update {
              it.copy(isSaving = false, error = uploadError!!.toUploadErrorText())
            }
            return@launch
          }
        }

        // 2. Delete pending-delete attachments (best-effort, parallel)
        val toDelete = state.pendingAttachments.filterIsInstance<PendingAttachment.PendingDelete>()
        if (toDelete.isNotEmpty()) {
          coroutineScope { toDelete.forEach { launch { attachmentManager.deleteFile(it.attachment) } } }
        }
      }

      // 3. Build final attachment list
      val savedLinks = state.pendingAttachments
        .filterIsInstance<PendingAttachment.LocalLink>()
        .map { link ->
          Attachment(
            id = link.tempId,
            name = link.name,
            type = AttachmentType.ATTACHMENT_TYPE_LINK,
            url = link.url,
            storage_path = "",
            download_url = "",
            mime_type = "",
            size_bytes = 0L,
          )
        }
      val savedFiles = state.pendingAttachments
        .filterIsInstance<PendingAttachment.Saved>()
        .map { it.attachment }
      val finalAttachments = savedFiles + uploadedAttachments + savedLinks

      // 4. Save log
      val componentSerial = when (state.selectedComponentType) {
        MaintenanceLog.ComponentType.AIRFRAME -> state.aircraft?.serial ?: ""
        else -> state.selectedSubComponent ?: ""
      }
      val now = Clock.System.now()
      val timestampInstant =
        state.maintenanceDate?.let { it.atStartOfDayIn(TimeZone.currentSystemDefault()) } ?: now
      val log = MaintenanceLog(
        id = resolvedLogId,
        timestamp = dev.fanfly.wingslog.core.ui.common.datetime.createWireInstant(
          timestampInstant.epochSeconds,
          timestampInstant.nanosecondsOfSecond
        ),
        work_description = state.workDescription,
        inspection_ids = state.selectedInspectionIds,
        engine_hour = state.engineTime.toDoubleOrNull() ?: 0.0,
        airframe_time = state.airframeTime.toDoubleOrNull() ?: 0.0,
        prop_time = state.propTime.toDoubleOrNull() ?: 0.0,
        component_type = state.selectedComponentType,
        component_serial = componentSerial,
        attachments = finalAttachments,
      )

      val result = if (isEditMode) logManager.updateLog(aircraftId, log) else logManager.addLog(
        aircraftId,
        log
      )
      result
        .onSuccess {
          state.selectedInspectionIds.forEach { cardId ->
            state.availableInspectionCards.find { it.id == cardId }?.let { card ->
              if (card.force_due_date != null || card.force_due_engine_hour > 0f) {
                inspectionManager.updateInspection(
                  aircraftId,
                  card.copy(
                    force_due_date = null,
                    force_due_engine_hour = 0f
                  )
                )
              }
            }
          }
          _events.send(MaintenanceLogFormEvent.SaveSuccess)
        }
        .onFailure { e ->
          _uiState.update {
            it.copy(
              isSaving = false,
              error = e.message?.let { UiText.DynamicString(it) }
                ?: UiText.StringRes(CoreRes.string.save_failed))
          }
        }
    }
  }

  // ── Delete ──────────────────────────────────────────────────────────────────

  fun deleteLog() {
    val id = logId ?: return
    viewModelScope.launch {
      // Best-effort: delete Storage files before removing the Firestore document
      if (!_uiState.value.isAnonymous) {
        val fileAttachments = _uiState.value.pendingAttachments
          .filterIsInstance<PendingAttachment.Saved>()
          .filter { it.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK }
        coroutineScope { fileAttachments.forEach { launch { attachmentManager.deleteFile(it.attachment) } } }
      }
      logManager.deleteLog(aircraftId, id)
        .onSuccess { _events.send(MaintenanceLogFormEvent.DeleteSuccess) }
        .onFailure { e ->
          _uiState.update {
            it.copy(error = e.message?.let { UiText.DynamicString(it) }
              ?: UiText.StringRes(CoreRes.string.delete_failed))
          }
        }
    }
  }
}

sealed interface MaintenanceLogFormEvent {
  data object SaveSuccess : MaintenanceLogFormEvent
  data object DeleteSuccess : MaintenanceLogFormEvent
  data object FileAdded : MaintenanceLogFormEvent
  data object LinkAdded : MaintenanceLogFormEvent
  data object PickError : MaintenanceLogFormEvent
}

private fun Throwable.toUploadErrorText(): UiText {
  val msg = message?.lowercase() ?: ""
  return when {
    msg.contains("network") || msg.contains("timeout") || msg.contains("unable to resolve") ->
      UiText.StringRes(AttachmentRes.string.upload_network_error)
    msg.contains("permission") || msg.contains("unauthorized") || msg.contains("403") ->
      UiText.StringRes(AttachmentRes.string.upload_permission_error)
    else -> UiText.StringRes(AttachmentRes.string.upload_failed)
  }
}
