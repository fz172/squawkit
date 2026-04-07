package dev.fanfly.wingslog.feature.maintenance.viewing.overview.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus

data class LogStats(
  val total: Long,
  val airframe: Long,
  val engine: Long,
  val propeller: Long,
  val currentEngineTime: Double? = null,
  val currentAirframeTime: Double? = null,
  val currentPropTime: Double? = null,
)

sealed interface AircraftOverviewUiState {
  data object Loading : AircraftOverviewUiState
  data class Success(
    val aircraft: Aircraft,
    val logStats: LogStats? = null,
    val activeInspections: List<InspectionCardWithStatus> = emptyList(),
    val compliedInspections: List<InspectionCardWithStatus> = emptyList(),
    // Detail sheet — non-null when user tapped a card
    val selectedInspection: InspectionCardWithStatus? = null,
    val logsForSelectedInspection: List<MaintenanceLog> = emptyList(),
    // Delete confirm dialog — non-null when user taps delete
    val deletingInspectionId: String? = null,
  ) : AircraftOverviewUiState

  data object Error : AircraftOverviewUiState
}
