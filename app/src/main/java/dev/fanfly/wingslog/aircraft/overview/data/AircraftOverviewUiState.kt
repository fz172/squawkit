package dev.fanfly.wingslog.aircraft.data

import dev.fanfly.wingslog.aircraft.Aircraft

sealed interface AircraftOverviewUiState {
    data object Loading : AircraftOverviewUiState
    data class Success(val aircraft: Aircraft) : AircraftOverviewUiState
    data object Error : AircraftOverviewUiState
}