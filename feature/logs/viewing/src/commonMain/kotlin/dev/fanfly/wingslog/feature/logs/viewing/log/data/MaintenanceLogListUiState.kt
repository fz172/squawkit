package dev.fanfly.wingslog.feature.logs.viewing.log.data

import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk

sealed interface MaintenanceLogListUiState {
  data object Loading : MaintenanceLogListUiState
  data class Success(
    val logs: List<MaintenanceLog>,
    val totalCount: Int,
    val filter: RecordFilter = RecordFilter(),
    val selectedLog: MaintenanceLog? = null,
    /** Whether the technician named on the selected log actually wrote it (design §7.5). */
    val selectedAuthorship: LogAuthorship = LogAuthorship.Unknown,
    val availableCards: List<MaintenanceTask> = emptyList(),
    val availableSquawks: List<Squawk> = emptyList(),
  ) : MaintenanceLogListUiState

  data object Error : MaintenanceLogListUiState
}
