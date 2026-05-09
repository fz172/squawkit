package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
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
    val activeTasks: List<MaintenanceTaskWithStatus> = emptyList(),
    val completedTasks: List<MaintenanceTaskWithStatus> = emptyList(),
    val selectedTask: MaintenanceTaskWithStatus? = null,
    val logsForSelectedTask: List<MaintenanceLog> = emptyList(),
    val deletingTaskId: String? = null,
    val syncStates: Map<String, BlobSyncState> = emptyMap(),
    val showLegacyAttachmentBanner: Boolean = false,
    val attachmentEnabled: Boolean = true,
  ) : AircraftOverviewUiState
}
