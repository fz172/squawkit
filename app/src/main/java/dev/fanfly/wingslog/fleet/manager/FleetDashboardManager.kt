package dev.fanfly.wingslog.fleet.manager

import com.google.firebase.firestore.ListenerRegistration
import dev.fanfly.wingslog.aircraft.Aircraft

interface FleetDashboardManager {
  /**
   * Loads the current user's aircraft from the data source.
   */
  fun observeFleetDashboard(licenseInfoListener: (result: List<Aircraft>) -> Unit): ListenerRegistration?

  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>
}