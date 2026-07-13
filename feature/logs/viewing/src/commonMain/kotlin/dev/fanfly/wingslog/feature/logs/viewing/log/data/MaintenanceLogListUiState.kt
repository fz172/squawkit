package dev.fanfly.wingslog.feature.logs.viewing.log.data

import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship

data class LogFilter(
  val query: String = "",
  val components: Set<ComponentType> = emptySet(),
) {
  val isActive: Boolean get() = query.isNotBlank() || components.isNotEmpty()
}

sealed interface MaintenanceLogListUiState {
  data object Loading : MaintenanceLogListUiState
  data class Success(
    val logs: List<MaintenanceLog>,
    val totalCount: Int,
    val filter: LogFilter = LogFilter(),
    val selectedLog: MaintenanceLog? = null,
    /** Whether the technician named on the selected log actually wrote it (design §7.5). */
    val selectedAuthorship: LogAuthorship = LogAuthorship.Unknown,
    val availableCards: List<MaintenanceTask> = emptyList(),
  ) : MaintenanceLogListUiState

  data object Error : MaintenanceLogListUiState
}
