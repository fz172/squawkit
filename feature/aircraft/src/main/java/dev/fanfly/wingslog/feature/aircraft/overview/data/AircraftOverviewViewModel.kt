package dev.fanfly.wingslog.feature.aircraft.overview.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AircraftOverviewViewModel @Inject constructor(
    private val aircraftManager: AircraftManager,
    private val logManager: MaintenanceLogManager,
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
                logManager.observeLogs(aircraftId)
            ) { aircraft, logs ->
                if (aircraft != null) {
                    val stats = LogStats(
                        total = logs.size.toLong(),
                        airframe = logs.count { it.componentType == MaintenanceLog.ComponentType.AIRFRAME }.toLong(),
                        engine = logs.count { it.componentType == MaintenanceLog.ComponentType.ENGINE }.toLong(),
                        propeller = logs.count { it.componentType == MaintenanceLog.ComponentType.PROPELLER }.toLong()
                    )
                    AircraftOverviewUiState.Success(aircraft = aircraft, logStats = stats)
                } else {
                    AircraftOverviewUiState.Error
                }
            }.collect { state ->
                _uiState.update { state }
            }
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
