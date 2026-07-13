package dev.fanfly.wingslog.feature.logs.viewing.log.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.LogAuthorship
import dev.fanfly.wingslog.feature.logs.datamanager.authorship.authorship
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The share-derived facts the log list needs, combined so they fit one slot of the outer combine. */
private data class AuthorshipContext(
  val authors: Map<String, String?>,
  val names: Map<String, String>,
  val isShared: Boolean,
  val hostedByOther: Boolean,
)

class MaintenanceLogListViewModel(
  private val logManager: MaintenanceLogManager,
  private val inspectionDataManager: TaskDataManager,
  private val sharingManager: SharingManager,
  private val technicianManager: TechnicianManager,
  private val auth: FirebaseAuth,
  val aircraftId: String,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow<MaintenanceLogListUiState>(MaintenanceLogListUiState.Loading)
  val uiState: StateFlow<MaintenanceLogListUiState> = _uiState.asStateFlow()

  private val _events = Channel<MaintenanceLogListEvent>()
  val events = _events.receiveAsFlow()

  private val _logsLoadState =
    MutableStateFlow<LogsLoadState>(LogsLoadState.Loading)
  private val _filter = MutableStateFlow(LogFilter())
  private val _selectedLog = MutableStateFlow<MaintenanceLog?>(null)
  private val _availableCards =
    MutableStateFlow<List<MaintenanceTask>>(emptyList())

  /** logId → uid of whoever wrote the latest revision. Envelope data, not payload (§7.5). */
  private val _logAuthors = MutableStateFlow<Map<String, String?>>(emptyMap())

  /** uid → display name, for naming the author of a log this member didn't write. */
  private val _namesByUid = MutableStateFlow<Map<String, String>>(emptyMap())

  /**
   * Attestation is a statement about *other people*. On an unshared aircraft nobody else can write a
   * log, so there is no one to attest against and nothing a reader could act on: the owner typed
   * every name here and is answerable for all of it. Saying "not verified" on their own logbook
   * would be noise dressed up as rigour, so authorship stays silent until the aircraft is shared.
   */
  private val _isShared = MutableStateFlow(false)

  /** Blobs live in the host's storage and are not shared in v1 (§9) — the rows say so. */
  private val _hostedByOther = MutableStateFlow(false)

  init {
    observeLogs()
    observeTasks()
    observeAuthorship()
    viewModelScope.launch {
      combine(
        _logsLoadState,
        _filter,
        _selectedLog,
        _availableCards,
        combine(
          _logAuthors,
          _namesByUid,
          _isShared,
          _hostedByOther,
        ) { authors, names, isShared, hostedByOther ->
          AuthorshipContext(authors, names, isShared, hostedByOther)
        },
      ) { logsState, filter, selectedLog, availableCards, ctx ->
        val (authors, names, isShared, hostedByOther) = ctx
        when (logsState) {
          LogsLoadState.Loading -> MaintenanceLogListUiState.Loading
          LogsLoadState.Error -> MaintenanceLogListUiState.Error
          is LogsLoadState.Loaded -> {
            val sorted = logsState.logs.sortedByDescending {
              it.timestamp?.getEpochSecond() ?: 0L
            }
            val filtered = sorted.filter { log ->
              (filter.components.isEmpty() || log.component_type in filter.components) &&
                (filter.query.isBlank() || log.work_description.contains(
                  filter.query,
                  ignoreCase = true
                ))
            }
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
              attachmentsUnavailable = hostedByOther,
              availableCards = availableCards,
            )
          }
        }
      }.collect { _uiState.value = it }
    }
  }

  private fun observeLogs() {
    viewModelScope.launch {
      logManager.observeLogs(aircraftId)
        .onStart { _logsLoadState.value = LogsLoadState.Loading }
        .catch { _logsLoadState.value = LogsLoadState.Error }
        .collect { logs -> _logsLoadState.value = LogsLoadState.Loaded(logs) }
    }
  }

  /**
   * Authorship, and the names to render it with. The roster of members who published a mirror gives
   * us a name for anyone who might have written a log on this aircraft; the caller's own record
   * covers the common case of their own writes.
   */
  private fun observeAuthorship() {
    viewModelScope.launch {
      sharingManager.observeIsShared(aircraftId)
        .catch { _isShared.value = false }
        .collect { _isShared.value = it }
    }
    viewModelScope.launch {
      sharingManager.observeHostedByOther(aircraftId)
        .catch { _hostedByOther.value = false }
        .collect { _hostedByOther.value = it }
    }
    viewModelScope.launch {
      logManager.observeLogAuthors(aircraftId)
        .catch { _logAuthors.value = emptyMap() }
        .collect { _logAuthors.value = it }
    }
    viewModelScope.launch {
      combine(
        sharingManager.observeLinkedTechnicians(aircraftId),
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
      inspectionDataManager.observeTasks(aircraftId)
        .catch { _availableCards.value = emptyList() }
        .collect { _availableCards.value = it }
    }
  }

  fun onSearchQueryChange(query: String) {
    _filter.value = _filter.value.copy(query = query)
  }

  fun onComponentFilterToggle(component: ComponentType) {
    val current = _filter.value.components
    _filter.value = _filter.value.copy(
      components = if (component in current) current - component else current + component
    )
  }

  fun clearFilter() {
    _filter.value = LogFilter()
  }

  fun retryLoading() {
    observeLogs()
    observeTasks()
  }

  fun onLogClick(log: MaintenanceLog) {
    _selectedLog.value = log
  }

  fun onDismissDetail() {
    _selectedLog.value = null
  }

  fun onAddLog() {
    viewModelScope.launch {
      _events.send(MaintenanceLogListEvent.NavigateToCreateLog(aircraftId))
    }
  }

  fun onEditLog(logId: String) {
    viewModelScope.launch {
      _events.send(MaintenanceLogListEvent.NavigateToEditLog(aircraftId, logId))
    }
  }

  private sealed interface LogsLoadState {
    data object Loading : LogsLoadState
    data object Error : LogsLoadState
    data class Loaded(val logs: List<MaintenanceLog>) : LogsLoadState
  }
}

sealed interface MaintenanceLogListEvent {
  data class NavigateToCreateLog(val aircraftId: String) :
    MaintenanceLogListEvent

  data class NavigateToEditLog(val aircraftId: String, val logId: String) :
    MaintenanceLogListEvent
}
