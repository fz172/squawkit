package dev.fanfly.wingslog.feature.aircraft.maintenance.log.data

import dev.fanfly.wingslog.aircraft.MaintenanceLog

data class LogFilter(
  val query: String = "",
  val component: MaintenanceLog.ComponentType? = null,
) {
  val isActive: Boolean get() = query.isNotBlank() || component != null
}

sealed interface MaintenanceLogListUiState {
  data object Loading : MaintenanceLogListUiState
  data class Success(
    val logs: List<MaintenanceLog>,
    val totalCount: Int,
    val filter: LogFilter = LogFilter(),
  ) : MaintenanceLogListUiState

  data object Error : MaintenanceLogListUiState
}
