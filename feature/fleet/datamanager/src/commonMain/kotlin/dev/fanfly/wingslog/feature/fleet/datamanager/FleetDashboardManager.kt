package dev.fanfly.wingslog.feature.fleet.datamanager

import dev.fanfly.wingslog.aircraft.Aircraft
import kotlinx.coroutines.flow.Flow

interface FleetDashboardManager {
  /**
   * Loads the current user's aircraft from the data source.
   */
  fun observeFleetDashboard(): Flow<List<Aircraft>>
}