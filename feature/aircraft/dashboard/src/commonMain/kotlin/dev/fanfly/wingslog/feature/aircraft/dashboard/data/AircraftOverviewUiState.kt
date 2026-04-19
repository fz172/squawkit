package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

data class LogStats(
  val total: Long,
  val airframe: Long,
  val engine: Long,
  val propeller: Long,
  val avionics: Long,
  val currentEngineTime: Double? = null,
  val currentAirframeTime: Double? = null,
  val currentPropTime: Double? = null,
)

sealed interface AircraftOverviewUiState {
  data object Loading : AircraftOverviewUiState
  data object Error : AircraftOverviewUiState

  data class Success(
    val aircraft: Aircraft,
    val logStats: LogStats? = null,
    val activeInspections: List<MaintenanceTaskWithStatus> = emptyList(),
    val compliedInspections: List<MaintenanceTaskWithStatus> = emptyList(),
    val selectedInspection: MaintenanceTaskWithStatus? = null,
    val logsForSelectedInspection: List<MaintenanceLog> = emptyList(),
    val deletingInspectionId: String? = null,
    val downloadingIds: Set<String> = emptySet(),
  ) : AircraftOverviewUiState
}
