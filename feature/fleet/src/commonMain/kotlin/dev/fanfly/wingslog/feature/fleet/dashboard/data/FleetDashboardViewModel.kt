package dev.fanfly.wingslog.feature.fleet.dashboard.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.fleet.database.FleetDashboardManager
import dev.fanfly.wingslog.feature.inspection.data.DueStatus
import dev.fanfly.wingslog.feature.inspection.database.InspectionManager
import dev.fanfly.wingslog.feature.maintenance.database.MaintenanceLogManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FleetDashboardViewModel(
  private val fleetDashboardManager: FleetDashboardManager,
  private val logManager: MaintenanceLogManager,
  private val inspectionManager: InspectionManager,
) : ViewModel() {

  private var fleetInfoJob: Job? = null

  private val _uiState: MutableStateFlow<FleetDashboardUiState> =
    MutableStateFlow(FleetDashboardUiState(isLoading = true))
  val uiState = _uiState.asStateFlow()

  init {
    loadFleetData()
  }

  private fun loadFleetData() {
    fleetInfoJob?.cancel()
    fleetInfoJob = viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      fleetDashboardManager.observeFleetDashboard()
        .flatMapLatest { fleet ->
          if (fleet.isEmpty()) {
            flowOf(fleet to emptyMap())
          } else {
            val perAircraftFlows = fleet.map { aircraft ->
              combine(
                inspectionManager.observeInspections(aircraft.id),
                logManager.observeLogs(aircraft.id)
              ) { inspections, logs ->
                aircraft.id to worstStatus(inspections, logs)
              }
            }
            combine(*perAircraftFlows.toTypedArray()) { statusArray ->
              fleet to statusArray.toMap()
            }
          }
        }
        .collect { (fleet, healthMap) ->
          _uiState.update {
            it.copy(fleet = fleet, aircraftHealthStatus = healthMap, isLoading = false)
          }
        }
    }
  }

  private suspend fun worstStatus(
    inspections: List<InspectionCard>,
    logs: List<MaintenanceLog>,
  ): DueStatus {
    if (inspections.isEmpty()) return DueStatus.NORMAL
    return inspections
      .map { card -> inspectionManager.computeNextDue(card, logs, inspections).status }
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
