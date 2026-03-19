package dev.fanfly.wingslog.feature.fleet.database.impl

import co.touchlab.kermit.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.database.common.AIRCRAFT_INFO_BLOB
import dev.fanfly.wingslog.core.database.common.getFleetCollectionRef
import dev.fanfly.wingslog.feature.fleet.database.FleetDashboardManager
class FleetDashboardManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
  ) : FleetDashboardManager {

  override fun observeFleetDashboard(fleetListener: (List<Aircraft>) -> Unit): ListenerRegistration? {
    val fleetDocumentRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return null

    val listener = fleetDocumentRef.addSnapshotListener { snapshot, e ->
      if (e != null) {
        logger.w(e) { "Listen failed." }
        return@addSnapshotListener
      }
      if (snapshot == null || snapshot.isEmpty) {
        logger.w { "No fleet data, returning empty" }
        fleetListener.invoke(listOf())
        return@addSnapshotListener

      }
      val fleet = snapshot.documents
      val result = mutableListOf<Aircraft>()
      for (aircraft in fleet) {
        val blob = aircraft.get(
          AIRCRAFT_INFO_BLOB
        ) as? Blob
        if (blob != null) {
          val aircraft = Aircraft.ADAPTER.decode(blob.toBytes())
          result += aircraft
          logger.i { "Recovered Aircraft: ${aircraft.tail_number} - ${aircraft.model}" }
        }
      }
      fleetListener.invoke(result)
      return@addSnapshotListener
    }

    return listener
  }


  companion object {
    private val logger = Logger.withTag("FleetDashboardManagerImpl")
  }
}