package dev.fanfly.wingslog.feature.logs.viewing.log.data

import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk

sealed interface MaintenanceLogListUiState {
  data object Loading : MaintenanceLogListUiState
  data class Success(
    val logs: List<MaintenanceLog>,
    /** Log id → the words the query matched, for highlighting. */
    val matches: Map<String, List<FieldMatch>> = emptyMap(),
    /** Every technician named on this thing’s logs, for the technician facet. */
    val technicians: List<String> = emptyList(),
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
