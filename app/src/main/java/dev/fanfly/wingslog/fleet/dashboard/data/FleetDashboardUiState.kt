package dev.fanfly.wingslog.fleet.dashboard.data

import dev.fanfly.wingslog.aircraft.Aircraft

/**
 * Holds the aircraft fleet dashboard data
 */
data class FleetDashboardUiState(
  val fleet: List<Aircraft> = listOf(),
  val isLoading: Boolean = false,
)