package dev.fanfly.wingslog.feature.aircraft.maintenance.log.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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

  init {
    observeLogs()
  }

  private fun observeLogs() {
    viewModelScope.launch {
      logManager.observeLogs(aircraftId)
        .onStart { _uiState.update { MaintenanceLogListUiState.Loading } }
        .catch { _uiState.update { MaintenanceLogListUiState.Error } }
        .collect { logs ->
          _uiState.update { MaintenanceLogListUiState.Success(logs) }
        }
    }
  }

  fun deleteLog(logId: String) {
    viewModelScope.launch {
      logManager.deleteLog(aircraftId, logId)
    }
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
}

sealed interface MaintenanceLogListEvent {
  data class NavigateToCreateLog(val aircraftId: String) : MaintenanceLogListEvent
  data class NavigateToEditLog(val aircraftId: String, val logId: String) : MaintenanceLogListEvent
}
