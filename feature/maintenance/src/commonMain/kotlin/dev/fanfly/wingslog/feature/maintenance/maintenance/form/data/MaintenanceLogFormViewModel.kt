package dev.fanfly.wingslog.feature.maintenance.maintenance.form.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.inspection.database.InspectionManager
import dev.fanfly.wingslog.feature.maintenance.database.AircraftManager
import dev.fanfly.wingslog.feature.maintenance.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
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
import wingslog.core.ui.generated.resources.delete_failed
import wingslog.core.ui.generated.resources.save_failed
import wingslog.feature.maintenance.generated.resources.log_not_found
import wingslog.feature.maintenance.generated.resources.work_description_required
import kotlin.time.Clock
import kotlin.time.Instant
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.maintenance.generated.resources.Res as AircraftRes

// removed uuid

class MaintenanceLogFormViewModel(
  private val logManager: MaintenanceLogManager,
  private val aircraftManager: AircraftManager,
  private val inspectionManager: InspectionManager,
  savedStateHandle: SavedStateHandle
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
    if (isEditMode) loadLog()
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
          )
        }
      } else {
        _uiState.update {
          it.copy(
            isLoading = false,
            error = UiText.StringRes(AircraftRes.string.log_not_found)
          )
        }
      }
    }
  }

  fun onMaintenanceDateChange(date: LocalDate?) {
    _uiState.update { it.copy(maintenanceDate = date) }
  }

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
      state.copy(
        selectedComponentType = value,
        selectedSubComponent = autoSerial
      )
    }
  }

  fun onSubComponentChange(serial: String?) {
    _uiState.update { it.copy(selectedSubComponent = serial) }
  }

  fun save() {
    val state = _uiState.value
    if (state.workDescription.isBlank()) {
      _uiState.update { it.copy(error = UiText.StringRes(AircraftRes.string.work_description_required)) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, error = null) }
      val componentSerial = when (state.selectedComponentType) {
        MaintenanceLog.ComponentType.AIRFRAME -> state.aircraft?.serial ?: ""
        else -> state.selectedSubComponent ?: ""
      }

      val now = Clock.System.now()
      val timestampInstant = state.maintenanceDate?.let { date ->
        date.atStartOfDayIn(TimeZone.currentSystemDefault())
      } ?: now

      val log = MaintenanceLog(
        id = logId ?: dev.fanfly.wingslog.core.database.generateRandomId(),
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
        component_serial = componentSerial
      )

      val result = if (isEditMode) {
        logManager.updateLog(aircraftId, log)
      } else {
        logManager.addLog(aircraftId, log)
      }

      result
        .onSuccess {
          // Reset overrides for connected inspections
          state.selectedInspectionIds.forEach { cardId ->
            state.availableInspectionCards.find { it.id == cardId }?.let { card ->
              if (card.force_due_date != null || card.force_due_engine_hour > 0f) {
                inspectionManager.updateInspection(
                  aircraftId,
                  card.copy(force_due_date = null, force_due_engine_hour = 0f)
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

  fun deleteLog() {
    val id = logId ?: return
    viewModelScope.launch {
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
}
