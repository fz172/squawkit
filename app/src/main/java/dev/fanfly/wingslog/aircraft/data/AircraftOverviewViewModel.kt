package dev.fanfly.wingslog.aircraft.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.fleet.dashboard.manager.FleetDashboardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AircraftOverviewViewModel @Inject constructor(
    private val fleetDashboardManager: FleetDashboardManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])
    private var fleetListener: ListenerRegistration? = null

    private val _uiState = MutableStateFlow<AircraftOverviewUiState>(AircraftOverviewUiState.Loading)
    val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()

    init {
        loadAircraft()
    }

    private fun loadAircraft() {
        _uiState.update { AircraftOverviewUiState.Loading }
        fleetListener = fleetDashboardManager.observeFleetDashboard { fleet ->
            val aircraft = fleet.find { it.id == aircraftId }
            if (aircraft != null) {
                _uiState.update { AircraftOverviewUiState.Success(aircraft) }
            } else {
                _uiState.update { AircraftOverviewUiState.Error }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fleetListener?.remove()
    }
}

