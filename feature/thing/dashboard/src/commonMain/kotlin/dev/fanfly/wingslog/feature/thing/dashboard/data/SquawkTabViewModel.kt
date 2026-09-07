package dev.fanfly.wingslog.feature.thing.dashboard.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.RecordFilterApplied
import dev.fanfly.wingslog.core.analytics.RecordSearch
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchHit
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.search.model.changesFrom
import dev.fanfly.wingslog.feature.search.model.debouncedQuery
import dev.fanfly.wingslog.feature.search.model.matchesById
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  /** Squawk id → the words the query matched, for highlighting. */
  val matches: Map<String, List<FieldMatch>> = emptyMap(),
)

/** The Squawks tab’s own list and filter. Logs are watched so an addressed squawk dates from the log that closed it. */
class SquawkTabViewModel(
  squawkManager: SquawkManager,
  logManager: MaintenanceLogManager,
  private val searchEngine: SearchEngine,
  private val tuning: SearchTuning,
  private val analytics: AnalyticsManager,
  thingId: String,
  private val templateId: String,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

  private val _filter = MutableStateFlow(RecordFilter())
  private var lastLoggedQuery = ""

  /** What is typed and chosen, updated synchronously so the search field never trails the caret. */
  val filter: StateFlow<RecordFilter> = _filter.asStateFlow()

  val uiState: StateFlow<SquawkTabUiState> = combine(
    squawkManager.observeSquawks(thingId)
      .map { squawks -> squawks.map { it.toWithStatus() } }
      .catch { emit(emptyList()) },
    logManager.observeLogs(thingId)
      .map { logs -> logs.mapNotNull { log -> log.timestamp?.let { log.id to it.toLocalDate(timeZone) } }.toMap() }
      .catch { emit(emptyMap()) },
    _filter,
    _filter.debouncedQuery(tuning.queryDebounceMillis),
  ) { squawks, logDates, typed, applied ->
    val today = clock.now().toLocalDateTime(timeZone).date
    val adapter = SquawkAdapter(timeZone, logDates)
    // The state carries what was typed; the search runs on the debounced copy.
    val hits = searchEngine.search(squawks, adapter, applied, today)
    trackSearch(applied.query, hits)
    SquawkTabUiState(typed, hits.map { it.item }, hits.matchesById { it.squawk.id })
  }.flowOn(tuning.dispatcher).stateIn(viewModelScope, SharingStarted.Eagerly, SquawkTabUiState())

  fun onFilterChange(filter: RecordFilter) {
    val previous = _filter.value
    _filter.value = filter
    filter.changesFrom(previous).forEach {
      analytics.log(RecordFilterApplied(templateId, TAB, it.kind, it.value))
    }
  }

  fun clearFilter() = onFilterChange(RecordFilter())

  private fun <T> trackSearch(query: String, hits: List<SearchHit<T>>) {
    if (query.isBlank() || query == lastLoggedQuery) return
    lastLoggedQuery = query
    analytics.log(RecordSearch(templateId, TAB, query.length, hits.size, hits.firstOrNull()?.explanations?.isNotEmpty() == true))
  }

  private companion object {
    const val TAB = "squawks"
  }
}
