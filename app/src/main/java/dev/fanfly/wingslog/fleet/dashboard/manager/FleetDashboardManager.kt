package dev.fanfly.wingslog.fleet.dashboard.manager

import com.google.firebase.firestore.ListenerRegistration
import dev.fanfly.wingslog.aircraft.Aircraft

interface FleetDashboardManager {
  /**
   * Loads the current user's aircraft from the data source.
   */
  fun observeFleetDashboard(fleetListener: (result: List<Aircraft>) -> Unit): ListenerRegistration?
}