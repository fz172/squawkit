package dev.fanfly.wingslog.feature.thing.dashboard.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.debouncedQuery
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class SquawkTabUiState(
  val filter: RecordFilter = RecordFilter(),
  /** Every status, narrowed by [filter]; the tab splits open from closed. */
  val squawks: List<SquawkWithStatus> = emptyList(),
)

/** The Squawks tab’s own list and filter. Logs are watched so an addressed squawk dates from the log that closed it. */
class SquawkTabViewModel(
  squawkManager: SquawkManager,
  logManager: MaintenanceLogManager,
  private val searchEngine: SearchEngine,
  thingId: String,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val queryDebounceMillis: Long = 150,
  private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

  private val _filter = MutableStateFlow(RecordFilter())

  val uiState: StateFlow<SquawkTabUiState> = combine(
    squawkManager.observeSquawks(thingId)
      .map { squawks -> squawks.map { it.toWithStatus() } }
      .catch { emit(emptyList()) },
    logManager.observeLogs(thingId)
      .map { logs -> logs.mapNotNull { log -> log.timestamp?.let { log.id to it.toLocalDate(timeZone) } }.toMap() }
      .catch { emit(emptyMap()) },
    _filter,
    _filter.debouncedQuery(queryDebounceMillis),
  ) { squawks, logDates, typed, applied ->
    val today = clock.now().toLocalDateTime(timeZone).date
    val adapter = SquawkAdapter(timeZone, logDates)
    // The state carries what was typed; the search runs on the debounced copy.
    SquawkTabUiState(typed, searchEngine.search(squawks, adapter, applied, today).map { it.item })
  }.flowOn(searchDispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, SquawkTabUiState())

  fun onFilterChange(filter: RecordFilter) {
    _filter.value = filter
  }

  fun clearFilter() {
    _filter.value = RecordFilter()
  }
}
