package dev.fanfly.wingslog.fleet.dashboard.manager.impl

import com.google.common.flogger.FluentLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.common.database.getFleetCollectionRef
import dev.fanfly.wingslog.fleet.dashboard.manager.FleetDashboardManager
import javax.inject.Inject

class FleetDashboardManagerImpl @Inject internal constructor(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : FleetDashboardManager {

  override fun observeFleetDashboard(fleetListener: (List<Aircraft>) -> Unit): ListenerRegistration? {
    val fleetDocumentRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return null

    val listener = fleetDocumentRef.addSnapshotListener { snapshot, e ->
      if (e != null) {
        logger.atWarning().withCause(e).log("Listen failed.")
        return@addSnapshotListener
      }
      if (snapshot == null || snapshot.isEmpty) {
        logger.atWarning().withCause(e).log("No fleet data, returning empty")
        fleetListener.invoke(listOf())
        return@addSnapshotListener

      }
      val fleet = snapshot.documents
      val result = mutableListOf<Aircraft>()
      for (aircraft in fleet) {
        // 1. Extract the Blob from the document
        val blob = aircraft.get("data") as? Blob
        if (blob != null) {
          val aircraft = Aircraft.parseFrom(blob.toBytes())
          result += aircraft
          // Now you have your typed object back!
          logger.atInfo().log("Recovered Aircraft: %s - %s", aircraft.tailNumber, aircraft.model)
        }
      }
      fleetListener.invoke(result)
      return@addSnapshotListener
    }

    return listener
  }


  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()

  }
}