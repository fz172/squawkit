package dev.fanfly.wingslog.feature.logs.viewing.log.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.QuickActionKind
import dev.fanfly.wingslog.core.analytics.QuickActionSource
import dev.fanfly.wingslog.core.analytics.QuickActionSurface
import dev.fanfly.wingslog.core.analytics.RecordFilterApplied
import dev.fanfly.wingslog.core.analytics.RecordQuickAction
import dev.fanfly.wingslog.core.analytics.RecordSearch
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.authorship
import dev.fanfly.wingslog.feature.search.datamanager.LogAdapter
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchHit
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.changesFrom
import dev.fanfly.wingslog.feature.search.model.debouncedQuery
import dev.fanfly.wingslog.feature.search.model.matchesById
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.logs.sharedassets.generated.resources.log_deleted
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes

/** The share-derived facts the log list needs, combined so they fit one slot of the outer combine. */
private data class AuthorshipContext(
  val authors: Map<String, String?>,
  val names: Map<String, String>,
  val isShared: Boolean,
)

/** Task cards and squawks a log can link to, resolved for the detail sheet's link rows. */
private data class LinkTargets(
  val cards: List<MaintenanceTask>,
  val squawks: List<Squawk>,
)

class MaintenanceLogListViewModel(
  private val logManager: MaintenanceLogManager,
  private val inspectionDataManager: TaskDataManager,
  private val sharingManager: SharingManager,
  private val technicianManager: TechnicianManager,
  private val squawkManager: SquawkManager,
  private val auth: FirebaseAuth,
  private val searchEngine: SearchEngine,
  private val tuning: SearchTuning,
  private val analytics: AnalyticsManager,
  private val templateRegistry: TemplateRegistry,
  val thingId: String,
  private val templateId: String,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

  private val logAdapter = LogAdapter(timeZone)
  private var lastLoggedQuery = ""

  private val _uiState =
    MutableStateFlow<MaintenanceLogListUiState>(MaintenanceLogListUiState.Loading)
  val uiState: StateFlow<MaintenanceLogListUiState> = _uiState.asStateFlow()

  // Buffered, not rendezvous: a quick action's snackbar can be emitted between the tab being torn
  // down and the new one attaching its collector, and a rendezvous send would park there.
  private val _events = Channel<MaintenanceLogListEvent>(Channel.BUFFERED)
  val events = _events.receiveAsFlow()

  private val _logsLoadState =
    MutableStateFlow<LogsLoadState>(LogsLoadState.Loading)
  private val _filter = MutableStateFlow(RecordFilter())

  /** What is typed and chosen, updated synchronously so the search field never trails the caret. */
  val filter: StateFlow<RecordFilter> = _filter.asStateFlow()
  private val _selectedLog = MutableStateFlow<MaintenanceLog?>(null)
  private val _deletingLog = MutableStateFlow<MaintenanceLog?>(null)
  private val _availableCards =
    MutableStateFlow<List<MaintenanceTask>>(emptyList())
  private val _availableSquawks = MutableStateFlow<List<Squawk>>(emptyList())

  /** logId → uid of whoever wrote the latest revision. Envelope data, not payload (§7.5). */
  private val _logAuthors = MutableStateFlow<Map<String, String?>>(emptyMap())

  /** uid → display name, for naming the author of a log this member didn't write. */
  private val _namesByUid = MutableStateFlow<Map<String, String>>(emptyMap())

  /**
   * Attestation is a statement about *other people*. On an unshared thing nobody else can write a
   * log, so there is no one to attest against and nothing a reader could act on: the owner typed
   * every name here and is answerable for all of it. Saying "not verified" on their own logbook
   * would be noise dressed up as rigour, so authorship stays silent until the thing is shared.
   */
  private val _isShared = MutableStateFlow(false)

  init {
    observeLogs()
    observeTasks()
    observeSquawks()
    observeAuthorship()
    viewModelScope.launch {
      combine(
        _logsLoadState,
        // The state carries what was typed; the search runs on the debounced copy.
        combine(_filter, _filter.debouncedQuery(tuning.queryDebounceMillis)) { typed, applied -> typed to applied },
        combine(_selectedLog, _deletingLog) { selected, deleting -> selected to deleting },
        combine(_availableCards, _availableSquawks) { cards, squawks ->
          LinkTargets(cards, squawks)
        },
        combine(
          _logAuthors,
          _namesByUid,
          _isShared,
        ) { authors, names, isShared ->
          AuthorshipContext(authors, names, isShared)
        },
      ) { logsState, filters, pending, linkTargets, ctx ->
        val (filter, applied) = filters
        val (selectedLog, deletingLog) = pending
        val (authors, names, isShared) = ctx
        when (logsState) {
          LogsLoadState.Loading -> MaintenanceLogListUiState.Loading
          LogsLoadState.Error -> MaintenanceLogListUiState.Error
          is LogsLoadState.Loaded -> {
            val sorted = logsState.logs.sortedByDescending {
              it.timestamp?.getEpochSecond() ?: 0L
            }
            val today = clock.now()
              .toLocalDateTime(timeZone).date
            val hits = searchEngine.search(sorted, logAdapter, applied, today)
            trackSearch(applied.query, hits)
            MaintenanceLogListUiState.Success(
              logs = hits.map { it.item },
              matches = hits.matchesById { it.id },
              technicians = logsState.logs.mapNotNull { it.technician?.name?.takeIf(String::isNotBlank) }.distinct().sorted(),
              totalCount = logsState.logs.size,
              allLogs = sorted,
              filter = filter,
              selectedLog = selectedLog,
              selectedAuthorship = selectedLog
                ?.takeIf { isShared }
                ?.authorship(
                  writerUid = authors[selectedLog.id],
                  nameForUid = { uid -> names[uid] },
                )
                ?: LogAuthorship.Unknown,
              availableCards = linkTargets.cards,
              availableSquawks = linkTargets.squawks,
              deletingLog = deletingLog,
            )
          }
        }
      }.flowOn(tuning.dispatcher).collect { _uiState.value = it }
    }
  }

  private fun observeLogs() {
    viewModelScope.launch {
      logManager.observeLogs(thingId)
        .onStart { _logsLoadState.value = LogsLoadState.Loading }
        .catch { _logsLoadState.value = LogsLoadState.Error }
        .collect { logs -> _logsLoadState.value = LogsLoadState.Loaded(logs) }
    }
  }

  /**
   * Authorship, and the names to render it with. The roster of members who published a mirror gives
   * us a name for anyone who might have written a log on this thing; the caller's own record
   * covers the common case of their own writes.
   */
  private fun observeAuthorship() {
    viewModelScope.launch {
      sharingManager.observeIsShared(thingId)
        .catch { _isShared.value = false }
        .collect { _isShared.value = it }
    }
    viewModelScope.launch {
      logManager.observeLogAuthors(thingId)
        .catch { _logAuthors.value = emptyMap() }
        .collect { _logAuthors.value = it }
    }
    viewModelScope.launch {
      combine(
        sharingManager.observeLinkedTechnicians(thingId),
        technicianManager.observeSelf(),
      ) { linked, self ->
        buildMap {
          linked.forEach { put(it.source_uid, it.name) }
          val myUid = auth.currentUser?.uid
          val myName = self?.name?.takeIf { it.isNotBlank() }
          if (myUid != null && myName != null) put(myUid, myName)
        }
      }
        .catch { _namesByUid.value = emptyMap() }
        .collect { _namesByUid.value = it }
    }
  }

  private fun observeTasks() {
    viewModelScope.launch {
      inspectionDataManager.observeTasks(thingId)
        .catch { _availableCards.value = emptyList() }
        .collect { _availableCards.value = it }
    }
  }

  private fun observeSquawks() {
    viewModelScope.launch {
      squawkManager.observeSquawks(thingId)
        .catch { _availableSquawks.value = emptyList() }
        .collect { _availableSquawks.value = it }
    }
  }

  fun onSearchQueryChange(query: String) {
    _filter.value = _filter.value.copy(query = query)
  }

  fun onComponentFilterToggle(component: ComponentType) = updateFilter { it.toggleComponent(component) }

  fun onTimeWindowChange(window: TimeWindow) = updateFilter { it.copy(time = window) }

  fun onFacetToggle(facet: Facet) = updateFilter { it.toggleFacet(facet) }

  fun clearFilter() = updateFilter { RecordFilter() }

  private fun updateFilter(transform: (RecordFilter) -> RecordFilter) {
    val previous = _filter.value
    val next = transform(previous)
    _filter.value = next
    next.changesFrom(previous).forEach {
      analytics.log(RecordFilterApplied(templateId, TAB, it.kind, it.value))
    }
  }

  private fun trackSearch(query: String, hits: List<SearchHit<MaintenanceLog>>) {
    if (query.isBlank() || query == lastLoggedQuery) return
    lastLoggedQuery = query
    analytics.log(RecordSearch(templateId, TAB, query.length, hits.size, hits.firstOrNull()?.explanations?.isNotEmpty() == true))
  }

  fun retryLoading() {
    observeLogs()
    observeTasks()
    observeSquawks()
  }

  fun onLogClick(log: MaintenanceLog) {
    _selectedLog.value = log
  }

  fun onDismissDetail() {
    _selectedLog.value = null
  }

  fun onAddLog() {
    viewModelScope.launch {
      _events.send(MaintenanceLogListEvent.NavigateToCreateLog(thingId))
    }
  }

  fun onEditLog(logId: String) {
    viewModelScope.launch {
      _events.send(MaintenanceLogListEvent.NavigateToEditLog(thingId, logId))
    }
  }

  fun onDeleteLogClick(log: MaintenanceLog) {
    _deletingLog.value = log
  }

  fun cancelDeleteLog() {
    _deletingLog.value = null
  }

  /**
   * Deletes through the manager and nothing lower: the tombstone it writes is what fans the
   * collaborator notification out (design §8). Logged at commit, so a cancelled dialog logs
   * nothing (PRD R25).
   */
  fun confirmDeleteLog() {
    val log = _deletingLog.value ?: return
    viewModelScope.launch {
      logManager.deleteLog(thingId, log.id)
        .onSuccess {
          analytics.log(
            RecordQuickAction(
              templateId = templateId,
              surface = QuickActionSurface.LOGS,
              action = QuickActionKind.DELETE,
              source = QuickActionSource.SWIPE,
            )
          )
          _events.send(
            MaintenanceLogListEvent.ShowMessage(
              UiText.StringRes(LogsRes.string.log_deleted, listOf(logWord()))
            )
          )
          if (_selectedLog.value?.id == log.id) _selectedLog.value = null
          _deletingLog.value = null
        }
        // The card stays and the dialog closes: the record is still there to try again on (R14).
        .onFailure {
          _deletingLog.value = null
          _events.send(
            MaintenanceLogListEvent.ShowMessage(UiText.StringRes(CoreRes.string.delete_failed))
          )
        }
    }
  }

  /** The word this build names a log with — the lexicon's, never a hard-coded "log" (R24). */
  private fun logWord(): String = LexiconFormatter.sentenceCase(
    templateRegistry.lexiconFor(templateRegistry.canonicalById(templateId)).logNoun
  )

  private sealed interface LogsLoadState {
    data object Loading : LogsLoadState
    data object Error : LogsLoadState
    data class Loaded(val logs: List<MaintenanceLog>) : LogsLoadState
  }
}

private const val TAB = "logs"

sealed interface MaintenanceLogListEvent {
  /** A snackbar for the section to post: a quick action's outcome, or a failure (design §7). */
  data class ShowMessage(val message: UiText) : MaintenanceLogListEvent

  data class NavigateToCreateLog(val thingId: String) :
    MaintenanceLogListEvent

  data class NavigateToEditLog(val thingId: String, val logId: String) :
    MaintenanceLogListEvent
}
