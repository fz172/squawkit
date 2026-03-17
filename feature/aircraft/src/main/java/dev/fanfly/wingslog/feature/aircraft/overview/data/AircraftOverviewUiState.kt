package dev.fanfly.wingslog.feature.aircraft.overview.data

import dev.fanfly.wingslog.aircraft.Aircraft

data class LogStats(
    val total: Long,
    val airframe: Long,
    val engine: Long,
    val propeller: Long,
    val currentTachTime: Double? = null,
    val currentAirframeTime: Double? = null,
    val currentPropTime: Double? = null
)

sealed interface AircraftOverviewUiState {
    data object Loading : AircraftOverviewUiState
    data class Success(
        val aircraft: Aircraft,
        val logStats: LogStats? = null
    ) : AircraftOverviewUiState
    data object Error : AircraftOverviewUiState
}
