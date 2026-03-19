package dev.fanfly.wingslog.feature.fleet.database.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.database.GITLIVE_AIRCRAFT_INFO_BLOB
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getGitLiveFleetCollectionRef
import dev.fanfly.wingslog.feature.fleet.database.FleetDashboardManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class FleetDashboardManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : FleetDashboardManager {

  override fun observeFleetDashboard(): Flow<List<Aircraft>> {
    val fleetCollectionRef =
      firestore.getGitLiveFleetCollectionRef(firebaseAuth) ?: return emptyFlow()

    return fleetCollectionRef.snapshots.map { snapshot ->
      if (snapshot.documents.isEmpty()) {
        Logger.w { "No fleet data, returning empty" }
        return@map emptyList()
      }

      val result = mutableListOf<Aircraft>()
      for (document in snapshot.documents) {
        // Wire 5.x uses camelCase for properties
        val blobBytes = document.getBlobAsBytes(GITLIVE_AIRCRAFT_INFO_BLOB)
        if (blobBytes == null || blobBytes.isEmpty()) {
          Logger.w { "Missing or empty aircraft info blob, skipping ${document.id}" }
          continue
        }

        try {
          val aircraft = Aircraft.ADAPTER.decode(blobBytes)
          result += aircraft
          Logger.i { "Recovered Aircraft: ${aircraft.tail_number} - ${aircraft.model}" }
        } catch (e: Exception) {
          Logger.e(e) { "Failed to decode aircraft" }
        }
      }
      result
    }.catch { e ->
      Logger.w(e) { "Listen failed." }
      emit(emptyList())
    }
  }

  companion object {
  }
}