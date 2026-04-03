package dev.fanfly.wingslog.feature.fleet.dashboard.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import dev.fanfly.wingslog.aircraft.DueStatus

/**
 * Holds the aircraft fleet dashboard data
 */
data class FleetDashboardUiState(
  val fleet: List<Aircraft> = listOf(),
  val aircraftLogOverview: Map<Aircraft, MaintenanceOverview> = mapOf(),
  val aircraftHealthStatus: Map<String, DueStatus> = emptyMap(),
  val isLoading: Boolean = false,
)