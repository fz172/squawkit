package dev.fanfly.wingslog.feature.aircraft.maintenance.form.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MaintenanceLogFormViewModel @Inject constructor(
    private val logManager: MaintenanceLogManager,
    private val aircraftManager: AircraftManager,
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
        if (isEditMode) loadLog()
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        workDescription = log.workDescription,
                        inspections = log.inspectionList,
                        tachTime = if (log.tachTime > 0.0) log.tachTime.toString() else "",
                        airframeTime = if (log.airframeTime > 0.0) log.airframeTime.toString() else "",
                        propTime = if (log.propTime > 0.0) log.propTime.toString() else "",
                        selectedComponentType = log.componentType,
                        selectedSubComponent = log.componentSerial.ifEmpty { null }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Log not found") }
            }
        }
    }

    fun onWorkDescriptionChange(value: String) = _uiState.update { it.copy(workDescription = value) }
    fun onInspectionsChange(value: List<MaintenanceLog.InspectionType>) = _uiState.update { it.copy(inspections = value) }
    fun onTachTimeChange(value: String) = _uiState.update { it.copy(tachTime = value) }
    fun onAirframeTimeChange(value: String) = _uiState.update { it.copy(airframeTime = value) }
    fun onPropTimeChange(value: String) = _uiState.update { it.copy(propTime = value) }

    fun onComponentTypeChange(value: MaintenanceLog.ComponentType) {
        _uiState.update { state ->
            // Auto-populate serial for AIRFRAME
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
            _uiState.update { it.copy(error = "Work description is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val now = System.currentTimeMillis() / 1000

            // Determine componentSerial from state
            val componentSerial = when (state.selectedComponentType) {
                MaintenanceLog.ComponentType.AIRFRAME -> state.aircraft?.serial ?: ""
                else -> state.selectedSubComponent ?: ""
            }

            val log = MaintenanceLog.newBuilder()
                .setId(logId ?: UUID.randomUUID().toString())
                .setTimestamp(Timestamp.newBuilder().setSeconds(now).build())
                .setWorkDescription(state.workDescription)
                .addAllInspection(state.inspections)
                .setTachTime(state.tachTime.toDoubleOrNull() ?: 0.0)
                .setAirframeTime(state.airframeTime.toDoubleOrNull() ?: 0.0)
                .setPropTime(state.propTime.toDoubleOrNull() ?: 0.0)
                .setComponentType(state.selectedComponentType)
                .setComponentSerial(componentSerial)
                .build()

            val result = if (isEditMode) {
                logManager.updateLog(aircraftId, log)
            } else {
                logManager.addLog(aircraftId, log)
            }

            result
                .onSuccess { _events.send(MaintenanceLogFormEvent.SaveSuccess) }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
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
                    _uiState.update { it.copy(error = e.message ?: "Delete failed") }
                }
        }
    }

sealed interface MaintenanceLogFormEvent {
    data object SaveSuccess : MaintenanceLogFormEvent
    data object DeleteSuccess : MaintenanceLogFormEvent
}
