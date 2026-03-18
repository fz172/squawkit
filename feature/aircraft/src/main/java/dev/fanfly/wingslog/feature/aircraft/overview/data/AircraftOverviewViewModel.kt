package dev.fanfly.wingslog.feature.aircraft.overview.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.TachRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.database.InspectionManager
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AircraftOverviewViewModel @Inject constructor(
    private val aircraftManager: AircraftManager,
    private val logManager: MaintenanceLogManager,
    private val inspectionManager: InspectionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])

    private val _uiState = MutableStateFlow<AircraftOverviewUiState>(AircraftOverviewUiState.Loading)
    val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<AircraftOverviewEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadAircraftAndStats()
    }

    private fun loadAircraftAndStats() {
        viewModelScope.launch {
            _uiState.update { AircraftOverviewUiState.Loading }
            combine(
                aircraftManager.loadAircraft(aircraftId),
                logManager.observeLogs(aircraftId),
                inspectionManager.observeInspections(aircraftId)
            ) { aircraft, logs, inspectionCards ->
                if (aircraft != null) {
                    val currentTachTime = logs.filter { it.tachTime > 0.0 }.maxOfOrNull { it.tachTime }
                    val currentAirframeTime = logs.filter { it.airframeTime > 0.0 }.maxOfOrNull { it.airframeTime }
                    val currentPropTime = logs.filter { it.propTime > 0.0 }.maxOfOrNull { it.propTime }
                    val stats = LogStats(
                        total = logs.size.toLong(),
                        airframe = logs.count { it.componentType == MaintenanceLog.ComponentType.AIRFRAME }.toLong(),
                        engine = logs.count { it.componentType == MaintenanceLog.ComponentType.ENGINE }.toLong(),
                        propeller = logs.count { it.componentType == MaintenanceLog.ComponentType.PROPELLER }.toLong(),
                        currentTachTime = currentTachTime,
                        currentAirframeTime = currentAirframeTime,
                        currentPropTime = currentPropTime
                    )
                    // Compute due status for each inspection card
                    val cardsWithStatus = inspectionCards.map { card ->
                        InspectionCardWithStatus(
                            card = card,
                            dueStatus = inspectionManager.computeNextDue(card, logs),
                        )
                    }
                    val current = _uiState.value
                    val showSheet = if (current is AircraftOverviewUiState.Success) current.showAddInspectionSheet else false
                    AircraftOverviewUiState.Success(
                        aircraft = aircraft,
                        logStats = stats,
                        inspectionCards = cardsWithStatus,
                        showAddInspectionSheet = showSheet,
                    )
                } else {
                    AircraftOverviewUiState.Error
                }
            }.collect { state ->
                _uiState.update { state }
            }
        }
    }

    fun showAddInspectionSheet() {
        _uiState.update { state ->
            if (state is AircraftOverviewUiState.Success) state.copy(showAddInspectionSheet = true) else state
        }
    }

    fun hideAddInspectionSheet() {
        _uiState.update { state ->
            if (state is AircraftOverviewUiState.Success) state.copy(showAddInspectionSheet = false) else state
        }
    }

    fun saveNewInspection(
        title: String,
        component: InspectionComponentType,
        rules: List<InspectionRule>,
    ) {
        val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
        viewModelScope.launch {
            val card = InspectionCard.newBuilder()
                .setTitle(title)
                .setComponent(component)
                .addAllRules(rules)
                .build()
            inspectionManager.addInspection(state.aircraft.id, card)
            hideAddInspectionSheet()
        }
    }

    fun deleteAircraft() {
        viewModelScope.launch {
            aircraftManager.deleteAircraft(aircraftId)
                .onSuccess {
                    _events.send(AircraftOverviewEvent.NavigateBack)
                }
                .onFailure { error ->
                    _events.send(AircraftOverviewEvent.ShowError(error.message ?: "Failed to delete aircraft"))
                }
        }
    }
}

sealed interface AircraftOverviewEvent {
    data object NavigateBack : AircraftOverviewEvent
    data class ShowError(val message: String) : AircraftOverviewEvent
}
