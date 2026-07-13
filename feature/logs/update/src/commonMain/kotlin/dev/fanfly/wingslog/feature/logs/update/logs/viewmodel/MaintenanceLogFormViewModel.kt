package dev.fanfly.wingslog.feature.logs.update.logs.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentFormController
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.merge.withoutAliasedTo
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.feature.attachment.sharedassets.generated.resources.add_file_failed
import wingslog.feature.attachment.sharedassets.generated.resources.file_too_large
import wingslog.feature.logs.update.generated.resources.log_not_found
import wingslog.feature.logs.update.generated.resources.work_description_required
import kotlin.time.Clock
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachmentRes
import wingslog.feature.logs.update.generated.resources.Res as MaintenanceRes

class MaintenanceLogFormViewModel(
  private val logManager: MaintenanceLogManager,
  private val fleetManager: FleetManager,
  private val inspectionDataManager: TaskDataManager,
  private val squawkManager: SquawkManager,
  private val attachmentManager: AttachmentManager,
  private val technicianManager: TechnicianManager,
  private val sharingManager: SharingManager,
  private val auth: FirebaseAuth,
  private val featureLabManager: FeatureLabManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val aircraftId: String = checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  private val logId: String? = savedStateHandle[Screen.LOG_ID]
  val isEditMode: Boolean get() = logId != null

  // Set when this screen is opened from the squawk edit screen's "Fixed" option, to preselect
  // and prefill this squawk once it shows up in observeSquawks() below.
  private val preselectedSquawkId: String? = savedStateHandle[Screen.SQUAWK_ID]
  private var preselectedSquawkSeeded = false

  // Both flags gate captureInitialSnapshot() below, so the unsaved-changes baseline isn't
  // captured until the squawk-preselect flow has had a chance to seed selectedSquawkIds.
  private var techniciansLoaded = false
  private var squawksLoaded = false

  private var saveJob: Job? = null
  private val attachmentForm =
    AttachmentFormController(attachmentManager, aircraftId)
  private val _uiState = MutableStateFlow(
    MaintenanceLogFormUiState(
      maintenanceDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
  )
  val uiState: StateFlow<MaintenanceLogFormUiState> = _uiState.asStateFlow()

  private val _events = Channel<MaintenanceLogFormEvent>()
  val events = _events.receiveAsFlow()

  init {
    loadAircraft()
    observeSquawks()
    observeTasks()
    observeTechnicians()
    checkAuth()
    observeFeatureFlags()
    // Mirror the shared attachment controller into UiState so the snapshot/dirty-check
    // logic keeps working off a single state object.
    attachmentForm.pendingAttachments
      .onEach { list -> _uiState.update { it.copy(pendingAttachments = list) } }
      .launchIn(viewModelScope)
    if (isEditMode) loadLog()
  }

  private fun observeFeatureFlags() {
    featureLabManager.observe()
      .onEach { flags ->
        _uiState.update {
          it.copy(
            attachmentUploadEnabled = flags.attachmentUploadEnabled,
          )
        }
      }
      .launchIn(viewModelScope)
  }

  private fun observeTechnicians() {
    combine(
      technicianManager.observeTechnicians(),
      technicianManager.observeSelfId(),
      // Members of THIS aircraft's share who published a mirror. Scoped to the aircraft because a
      // log is signed for one aircraft — only its members are selectable (§7.3).
      sharingManager.observeLinkedTechnicians(aircraftId),
    ) { technicians, selfId, linked ->
      // Stamp the self-record with its owning uid so picking it snapshots provenance into the log
      // (§7.3). Manual entries carry no source_uid — they were typed by hand, not linked to an
      // account. Linked mirrors arrive already stamped with their owner's uid.
      val self = technicians.find { it.id == selfId }
        ?.let { it.copy(source_uid = auth.currentUser?.uid.orEmpty()) }
      // A manual row the user aliased to a member (§7.4) is hidden only where that member's mirror
      // actually appears — same rule the technician list applies, so the two can't drift.
      val others = technicians
        .filter { it.id != selfId }
        .withoutAliasedTo(linked)
        .sortedBy { it.name.lowercase() }
      Triple(self, listOfNotNull(self) + others, linked)
    }
      .onEach { (selfTech, available, linked) ->
        _uiState.update { state ->
          val newSelected = state.selectedTechnician ?: selfTech
          state.copy(
            availableTechnicians = available,
            linkedTechnicians = linked,
            selfTechnicianId = selfTech?.id,
            selectedTechnician = newSelected,
          )
        }
        techniciansLoaded = true
        maybeCaptureInitialSnapshot()
      }
      .launchIn(viewModelScope)
  }

  private fun maybeCaptureInitialSnapshot() {
    // Also wait for a pending squawk-title prefill to be consumed (see
    // consumeResolveSquawkPrefill below), so the baseline includes the final prefilled
    // workDescription too and doesn't read as an unsaved change the instant it lands.
    if (!isEditMode && techniciansLoaded && squawksLoaded &&
      _uiState.value.pendingResolveSquawkTitle == null
    ) {
      captureInitialSnapshot()
    }
  }

  private fun captureInitialSnapshot() {
    _uiState.update { state ->
      if (state.initialSnapshot != null) state
      else state.copy(initialSnapshot = state.currentSnapshot())
    }
  }

  private fun checkAuth() {
    val user = auth.currentUser
    _uiState.update { it.copy(isAnonymous = user == null || user.isAnonymous) }
  }

  private fun observeSquawks() {
    squawkManager.observeSquawks(aircraftId)
      .onEach { squawks ->
        val filtered = squawks.filter { s ->
          s.addressed_by_log_id.isEmpty() || s.addressed_by_log_id == logId
        }
        _uiState.update { state ->
          var next = state.copy(availableSquawks = filtered)
          if (!isEditMode && !preselectedSquawkSeeded && preselectedSquawkId != null) {
            filtered.find { it.id == preselectedSquawkId }
              ?.let { squawk ->
                next = next.copy(
                  selectedSquawkIds = (next.selectedSquawkIds + squawk.id).distinct(),
                  pendingResolveSquawkTitle = squawk.title,
                )
                // Only latch once the squawk is actually found — an empty/transitional first
                // emission (e.g. while auth is still resolving) must not permanently skip this;
                // it should simply retry on the next emission.
                preselectedSquawkSeeded = true
              }
          }
          next
        }
        squawksLoaded = true
        maybeCaptureInitialSnapshot()
      }
      .launchIn(viewModelScope)
  }

  /**
   * Called by the screen once it has resolved [MaintenanceLogFormUiState.pendingResolveSquawkTitle]
   * into a localized string, to prepend it to the work description and clear the pending marker.
   *
   * Blank input is ignored rather than clearing the pending marker: on web, `stringResource`
   * resolves the format string asynchronously and composes with an empty default first, so a
   * transient "" must not permanently drop the real value that arrives moments later.
   */
  fun consumeResolveSquawkPrefill(workDescription: String) {
    if (workDescription.isBlank()) return
    _uiState.update {
      it.copy(
        // Prepend rather than overwrite, so text the user already typed while this was
        // resolving asynchronously isn't lost.
        workDescription = if (it.workDescription.isBlank()) workDescription
        else "$workDescription\n${it.workDescription}",
        pendingResolveSquawkTitle = null,
      )
    }
    maybeCaptureInitialSnapshot()
  }

  private fun observeTasks() {
    inspectionDataManager.observeTasks(aircraftId)
      .onEach { cards ->
        _uiState.update { it.copy(availableInspectionCards = cards) }
      }
      .launchIn(viewModelScope)
  }

  private fun loadAircraft() {
    viewModelScope.launch {
      fleetManager.loadAircraft(aircraftId)
        .collect { aircraft ->
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
            Instant.fromEpochSeconds(
              epochSec,
              ts.getNano()
            )
              .toLocalDateTime(TimeZone.currentSystemDefault()).date
          } else null
        }
        // Seed the controller, then copy its value into UiState synchronously so the
        // initial snapshot below already includes the saved attachments.
        attachmentForm.seedIfEmpty(log.attachments)
        val existingAttachments = attachmentForm.pendingAttachments.value
        _uiState.update {
          it.copy(
            isLoading = false,
            workDescription = log.work_description,
            selectedSquawkIds = log.squawk_ids,
            selectedInspectionIds = log.inspection_ids,
            selectedTechnician = log.technician ?: it.selectedTechnician,
            engineTime = if (log.engine_hour > 0.0) log.engine_hour.toString() else "",
            airframeTime = if (log.airframe_time > 0.0) log.airframe_time.toString() else "",
            propTime = if (log.prop_time > 0.0) log.prop_time.toString() else "",
            selectedComponentType = log.component_type,
            selectedSubComponent = log.component_serial.ifEmpty { null },
            maintenanceDate = logDate,
            pendingAttachments = existingAttachments,
          )
        }
        captureInitialSnapshot()
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

  fun onWorkDescriptionChange(value: String) =
    _uiState.update { it.copy(workDescription = value) }

  fun onTechnicianSelect(technician: Technician?) =
    _uiState.update {
      it.copy(
        selectedTechnician = technician,
        showTechnicianPicker = false
      )
    }

  fun showTechnicianPicker() =
    _uiState.update { it.copy(showTechnicianPicker = true) }

  fun hideTechnicianPicker() =
    _uiState.update { it.copy(showTechnicianPicker = false) }

  fun showInspectionPicker() =
    _uiState.update { it.copy(showInspectionPicker = true) }

  fun hideInspectionPicker() =
    _uiState.update { it.copy(showInspectionPicker = false) }

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

  fun showSquawkPicker() = _uiState.update { it.copy(showSquawkPicker = true) }
  fun hideSquawkPicker() = _uiState.update { it.copy(showSquawkPicker = false) }
  fun toggleSquawkSelection(squawkId: String) {
    _uiState.update { state ->
      val current = state.selectedSquawkIds.toMutableList()
      if (squawkId in current) current.remove(squawkId) else current.add(
        squawkId
      )
      state.copy(selectedSquawkIds = current)
    }
  }

  fun removeSquawkId(squawkId: String) {
    _uiState.update { state ->
      state.copy(selectedSquawkIds = state.selectedSquawkIds.filter { it != squawkId })
    }
  }

  fun onEngineTimeChange(value: String) =
    _uiState.update { it.copy(engineTime = value) }

  fun onAirframeTimeChange(value: String) =
    _uiState.update { it.copy(airframeTime = value) }

  fun onPropTimeChange(value: String) =
    _uiState.update { it.copy(propTime = value) }

  fun onComponentTypeChange(value: ComponentType) {
    _uiState.update { state ->
      val aircraft = state.aircraft
      val autoSerial = when (value) {
        ComponentType.COMPONENT_AIRFRAME ->
          aircraft?.serial?.takeIf { it.isNotEmpty() }

        ComponentType.COMPONENT_ENGINE -> {
          val engines = aircraft?.engine ?: emptyList()
          engines.singleOrNull()?.serial?.takeIf { it.isNotEmpty() }
        }

        ComponentType.COMPONENT_PROPELLER -> {
          val propSerials = aircraft?.engine?.flatMap { engine ->
            buildList {
              engine.propeller?.hub?.serial?.takeIf { it.isNotEmpty() }
                ?.let { add(it) }
              engine.propeller?.blades?.forEach { blade ->
                blade.serial.takeIf { it.isNotEmpty() }
                  ?.let { add(it) }
              }
            }
          } ?: emptyList()
          propSerials.singleOrNull()
        }

        else -> null
      }
      state.copy(
        selectedComponentType = value,
        selectedSubComponent = autoSerial
      )
    }
  }

  fun onSubComponentChange(serial: String?) =
    _uiState.update { it.copy(selectedSubComponent = serial) }

  // ── Attachments ─────────────────────────────────────────────────────────────

  fun showAttachmentPicker() =
    _uiState.update { it.copy(showAttachmentPicker = true) }

  fun hideAttachmentPicker() =
    _uiState.update { it.copy(showAttachmentPicker = false) }

  fun onFilePickError() {
    viewModelScope.launch { _events.send(MaintenanceLogFormEvent.PickError) }
  }

  fun addLocalFiles(files: List<PickedFile>) {
    viewModelScope.launch {
      val anyAdded = attachmentForm.addLocalFiles(files) { error ->
        _uiState.update {
          it.copy(
            error = when (error) {
              is AttachmentFormController.AddFileError.FileTooLarge ->
                UiText.StringRes(AttachmentRes.string.file_too_large)

              is AttachmentFormController.AddFileError.Failed ->
                error.message?.let { msg -> UiText.DynamicString(msg) }
                  ?: UiText.StringRes(AttachmentRes.string.add_file_failed)
            }
          )
        }
      }
      if (anyAdded) _events.send(MaintenanceLogFormEvent.FileAdded)
    }
  }

  fun addLink(
    url: String,
    name: String,
  ) {
    attachmentForm.addLink(url, name)
    viewModelScope.launch { _events.send(MaintenanceLogFormEvent.LinkAdded) }
  }

  fun removeAttachment(id: String) {
    attachmentForm.remove(id)
  }

  // ── Save ────────────────────────────────────────────────────────────────────

  fun save() {
    val state = _uiState.value
    if (state.workDescription.isBlank()) {
      _uiState.update { it.copy(error = UiText.StringRes(MaintenanceRes.string.work_description_required)) }
      return
    }
    saveJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          isSaving = true,
          error = null
        )
      }

      val resolvedLogId = logId ?: generateRandomId()

      // Tombstone deleted attachments and build the final list
      val finalAttachments = attachmentForm.resolveForSave()

      // Save log
      val componentSerial = when (state.selectedComponentType) {
        ComponentType.COMPONENT_AIRFRAME -> state.aircraft?.serial ?: ""
        else -> state.selectedSubComponent ?: ""
      }
      val now = Clock.System.now()
      val timestampInstant =
        state.maintenanceDate?.atStartOfDayIn(TimeZone.currentSystemDefault())
          ?: now
      val log = MaintenanceLog(
        id = resolvedLogId,
        timestamp = toWireInstant(
          timestampInstant.epochSeconds,
          timestampInstant.nanosecondsOfSecond
        ),
        work_description = state.workDescription,
        squawk_ids = state.selectedSquawkIds,
        inspection_ids = state.selectedInspectionIds,
        engine_hour = state.engineTime.toDoubleOrNull() ?: 0.0,
        airframe_time = state.airframeTime.toDoubleOrNull() ?: 0.0,
        prop_time = state.propTime.toDoubleOrNull() ?: 0.0,
        component_type = state.selectedComponentType,
        component_serial = componentSerial,
        attachments = finalAttachments,
        technician = state.selectedTechnician,
      )

      val result = if (isEditMode) logManager.updateLog(
        aircraftId,
        log
      ) else logManager.addLog(
        aircraftId,
        log
      )
      result
        .onSuccess {
          if (state.selectedSquawkIds.isNotEmpty()) {
            squawkManager.markAddressed(
              aircraftId,
              state.selectedSquawkIds,
              resolvedLogId
            )
          }
          state.selectedInspectionIds.forEach { cardId ->
            state.availableInspectionCards.find { it.id == cardId }
              ?.let { card ->
                if (card.force_due_date != null || card.force_due_engine_hour > 0f ||
                  card.force_complied_status != null
                ) {
                  inspectionDataManager.updateTask(
                    aircraftId,
                    card.copy(
                      force_due_date = null,
                      force_due_engine_hour = 0f,
                      force_complied_status = null,
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
      // Tombstone file attachments before removing the Firestore document
      attachmentForm.deleteSavedFiles()
      logManager.deleteLog(
        aircraftId,
        id
      )
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
