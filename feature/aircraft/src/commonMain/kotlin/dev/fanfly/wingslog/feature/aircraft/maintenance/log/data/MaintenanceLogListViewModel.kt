package dev.fanfly.wingslog.feature.aircraft.maintenance.log.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MaintenanceLogListViewModel(
  private val logManager: MaintenanceLogManager,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])

  private val _uiState =
    MutableStateFlow<MaintenanceLogListUiState>(MaintenanceLogListUiState.Loading)
  val uiState: StateFlow<MaintenanceLogListUiState> = _uiState.asStateFlow()

  private val _events = Channel<MaintenanceLogListEvent>()
  val events = _events.receiveAsFlow()

  private val _logsLoadState = MutableStateFlow<LogsLoadState>(LogsLoadState.Loading)
  private val _filter = MutableStateFlow(LogFilter())

  init {
    observeLogs()
    viewModelScope.launch {
      combine(_logsLoadState, _filter) { logsState, filter ->
        when (logsState) {
          LogsLoadState.Loading -> MaintenanceLogListUiState.Loading
          LogsLoadState.Error -> MaintenanceLogListUiState.Error
          is LogsLoadState.Loaded -> {
            val filtered = logsState.logs.filter { log ->
              (filter.component == null || log.component_type == filter.component) &&
                (filter.query.isBlank() || log.work_description.contains(
                  filter.query,
                  ignoreCase = true
                ))
            }
            MaintenanceLogListUiState.Success(filtered, logsState.logs.size, filter)
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

  fun onSearchQueryChange(query: String) {
    _filter.value = _filter.value.copy(query = query)
  }

  fun onComponentFilterChange(component: MaintenanceLog.ComponentType?) {
    _filter.value = _filter.value.copy(component = component)
  }

  fun clearFilter() {
    _filter.value = LogFilter()
  }

  fun deleteLog(logId: String) {
    viewModelScope.launch {
      logManager.deleteLog(aircraftId, logId)
    }
  }

  fun retryLoading() {
    observeLogs()
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
  data class NavigateToCreateLog(val aircraftId: String) : MaintenanceLogListEvent
  data class NavigateToEditLog(val aircraftId: String, val logId: String) : MaintenanceLogListEvent
}
