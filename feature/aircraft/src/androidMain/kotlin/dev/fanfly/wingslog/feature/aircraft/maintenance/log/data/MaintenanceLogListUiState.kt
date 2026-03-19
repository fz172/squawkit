package dev.fanfly.wingslog.feature.aircraft.maintenance.log.data

import dev.fanfly.wingslog.aircraft.MaintenanceLog

sealed interface MaintenanceLogListUiState {
  data object Loading : MaintenanceLogListUiState
  data class Success(val logs: List<MaintenanceLog>) : MaintenanceLogListUiState
  data object Error : MaintenanceLogListUiState
}
