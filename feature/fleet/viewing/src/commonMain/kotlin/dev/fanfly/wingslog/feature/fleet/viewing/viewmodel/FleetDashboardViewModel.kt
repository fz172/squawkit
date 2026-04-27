package dev.fanfly.wingslog.feature.fleet.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FleetDashboardViewModel(
  private val fleetManager: FleetManager,
  private val logManager: MaintenanceLogManager,
  private val taskDataManager: TaskDataManager,
  private val taskDueManager: TaskDueManager,
) : ViewModel() {

  private var fleetInfoJob: Job? = null

  private val _uiState: MutableStateFlow<FleetDashboardUiState> =
    MutableStateFlow(FleetDashboardUiState(isLoading = true))
  val uiState = _uiState.asStateFlow()

  init {
    loadFleetData()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun loadFleetData() {
    fleetInfoJob?.cancel()
    fleetInfoJob = viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      fleetManager.observeFleetDashboard()
        .flatMapLatest { fleet ->
          if (fleet.isEmpty()) {
            flowOf(fleet to emptyMap())
          } else {
            val perAircraftFlows = fleet.map { aircraft ->
              combine(
                taskDataManager.observeTasks(aircraft.id),
                logManager.observeLogs(aircraft.id)
              ) { tasks, logs ->
                aircraft.id to worstStatus(
                  tasks,
                  logs
                )
              }
            }
            combine(*perAircraftFlows.toTypedArray()) { statusArray ->
              fleet to statusArray.toMap()
            }
          }
        }
        .collect { (fleet, healthMap) ->
          _uiState.update {
            it.copy(
              fleet = fleet,
              aircraftHealthStatus = healthMap,
              isLoading = false
            )
          }
        }
    }
  }

  private fun worstStatus(
    tasks: List<MaintenanceTask>,
    logs: List<MaintenanceLog>,
  ): DueStatus {
    if (tasks.isEmpty()) return DueStatus.NORMAL
    return tasks
      .map { card ->
        taskDueManager.computeNextDue(
          card,
          logs,
          tasks
        ).status
      }
      .maxByOrNull { it.severity() }
      ?: DueStatus.NORMAL
  }

  private fun DueStatus.severity(): Int = when (this) {
    DueStatus.OVERDUE -> 3
    DueStatus.DUE_SOON -> 2
    DueStatus.NORMAL -> 1
    DueStatus.COMPLIED -> 0
  }
}
