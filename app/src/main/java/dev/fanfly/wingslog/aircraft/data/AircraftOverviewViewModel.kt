package dev.fanfly.wingslog.aircraft.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.manager.AircraftManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AircraftOverviewViewModel @Inject constructor(
    private val aircraftManager: AircraftManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])

    private val _uiState = MutableStateFlow<AircraftOverviewUiState>(AircraftOverviewUiState.Loading)
    val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()

    init {
        loadAircraft()
    }

    private fun loadAircraft() {
        viewModelScope.launch {
            aircraftManager.loadAircraft(aircraftId)
                .onStart { _uiState.update { AircraftOverviewUiState.Loading } }
                .collect { aircraft ->
                    if (aircraft != null) {
                        _uiState.update { AircraftOverviewUiState.Success(aircraft) }
                    } else {
                        // If aircraft is null, it might be deleted or not found
                        _uiState.update { AircraftOverviewUiState.Error }
                    }
                }
        }
    }


}

