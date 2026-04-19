package dev.fanfly.wingslog.feature.logs.viewing.log.data

import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask

data class LogFilter(
  val query: String = "",
  val component: ComponentType? = null,
) {
  val isActive: Boolean get() = query.isNotBlank() || component != null
}

sealed interface MaintenanceLogListUiState {
  data object Loading : MaintenanceLogListUiState
  data class Success(
    val logs: List<MaintenanceLog>,
    val totalCount: Int,
    val filter: LogFilter = LogFilter(),
    val selectedLog: MaintenanceLog? = null,
    val availableCards: List<MaintenanceTask> = emptyList(),
  ) : MaintenanceLogListUiState

  data object Error : MaintenanceLogListUiState
}
