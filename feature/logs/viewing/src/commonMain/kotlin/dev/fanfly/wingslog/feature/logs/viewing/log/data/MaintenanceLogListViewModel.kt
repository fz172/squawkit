package dev.fanfly.wingslog.feature.logs.viewing.log.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.authorship
import dev.fanfly.wingslog.feature.search.datamanager.LogAdapter
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.debouncedQuery
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
  val thingId: String,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val queryDebounceMillis: Long = 150,
  private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

  private val logAdapter = LogAdapter(timeZone)

  private val _uiState =
    MutableStateFlow<MaintenanceLogListUiState>(MaintenanceLogListUiState.Loading)
  val uiState: StateFlow<MaintenanceLogListUiState> = _uiState.asStateFlow()

  private val _events = Channel<MaintenanceLogListEvent>()
  val events = _events.receiveAsFlow()

  private val _logsLoadState =
    MutableStateFlow<LogsLoadState>(LogsLoadState.Loading)
  private val _filter = MutableStateFlow(RecordFilter())
  private val _selectedLog = MutableStateFlow<MaintenanceLog?>(null)
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
        _filter.debouncedQuery(queryDebounceMillis),
        _selectedLog,
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
      ) { logsState, filter, selectedLog, linkTargets, ctx ->
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
            val filtered =
              searchEngine.search(sorted, logAdapter, filter, today)
                .map { it.item }
            MaintenanceLogListUiState.Success(
              logs = filtered,
              totalCount = logsState.logs.size,
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
            )
          }
        }
      }.flowOn(searchDispatcher).collect { _uiState.value = it }
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

  fun onComponentFilterToggle(component: ComponentType) {
    _filter.value = _filter.value.toggleComponent(component)
  }

  fun onTimeWindowChange(window: TimeWindow) {
    _filter.value = _filter.value.copy(time = window)
  }

  fun clearFilter() {
    _filter.value = RecordFilter()
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

  private sealed interface LogsLoadState {
    data object Loading : LogsLoadState
    data object Error : LogsLoadState
    data class Loaded(val logs: List<MaintenanceLog>) : LogsLoadState
  }
}

sealed interface MaintenanceLogListEvent {
  data class NavigateToCreateLog(val thingId: String) :
    MaintenanceLogListEvent

  data class NavigateToEditLog(val thingId: String, val logId: String) :
    MaintenanceLogListEvent
}
