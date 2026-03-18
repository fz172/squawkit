package dev.fanfly.wingslog.feature.aircraft.overview.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus

data class LogStats(
    val total: Long,
    val airframe: Long,
    val engine: Long,
    val propeller: Long,
    val currentTachTime: Double? = null,
    val currentAirframeTime: Double? = null,
    val currentPropTime: Double? = null
)

data class InspectionCardWithStatus(
    val card: InspectionCard,
    val dueStatus: DueStatus,
)

sealed interface AircraftOverviewUiState {
    data object Loading : AircraftOverviewUiState
    data class Success(
        val aircraft: Aircraft,
        val logStats: LogStats? = null,
        val inspectionCards: List<InspectionCardWithStatus> = emptyList(),
        val showAddInspectionSheet: Boolean = false,
        // Detail sheet — non-null when user tapped a card
        val selectedInspection: InspectionCardWithStatus? = null,
        val logsForSelectedInspection: List<dev.fanfly.wingslog.aircraft.MaintenanceLog> = emptyList(),
        // Edit sheet — non-null when user opens edit from detail sheet
        val editingInspection: InspectionCardWithStatus? = null,
        // Delete confirm dialog — non-null when user taps delete
        val deletingInspectionId: String? = null,
    ) : AircraftOverviewUiState
    data object Error : AircraftOverviewUiState
}
