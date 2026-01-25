package dev.fanfly.wingslog.fleet.manager.impl

import com.google.common.flogger.FluentLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.copy
import dev.fanfly.wingslog.dev.fanfly.wingslog.common.database.getUserDocumentRef
import dev.fanfly.wingslog.fleet.manager.FleetDashboardManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FleetDashboardManagerImpl @Inject internal constructor(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : FleetDashboardManager {

  override fun observeFleetDashboard(fleetListener: (List<Aircraft>) -> Unit): ListenerRegistration? {
    val fleetDocumentRef = getFleetRef() ?: return null

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

  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> = try {
    val fleetRef = getFleetRef() ?: return Result.failure(Exception("Fleet reference is null"))
    val aircraftRef = getAircraftRef(aircraft) ?: fleetRef.document()
    val aircraftWithId = aircraft.copy {
      id = aircraftRef.id
    }
    val data = mapOf(AIRCRAFT_INFO_BLOB to Blob.fromBytes(aircraftWithId.toByteArray()))

    // Use SetOptions.merge() to only update this field
    aircraftRef.set(data, SetOptions.merge()).await()
    logger.atFine().log("Aircraft updated successfully.")
    Result.success(true)
  } catch (e: Exception) {
    logger.atWarning().withCause(e).log("Error updating profile")

    Result.failure(e)
  }


  private fun getFleetRef(): CollectionReference? =
    firestore.getUserDocumentRef(firebaseAuth)?.collection(FLEET_COLLECTION)

  private fun getAircraftRef(aircraft: Aircraft): DocumentReference? = if (aircraft.id.isEmpty()) {
    null
  } else {
    firestore.getUserDocumentRef(firebaseAuth)?.collection(FLEET_COLLECTION)?.document(aircraft.id)
  }


  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()

    private const val FLEET_COLLECTION = "fleet"
    private const val AIRCRAFT_INFO_BLOB = "aircraft_info_blob"
  }
}