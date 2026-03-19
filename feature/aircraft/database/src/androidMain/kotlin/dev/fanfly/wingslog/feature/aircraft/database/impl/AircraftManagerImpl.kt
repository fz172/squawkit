package dev.fanfly.wingslog.feature.aircraft.database.impl

import co.touchlab.kermit.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.database.common.AIRCRAFT_INFO_BLOB
import dev.fanfly.wingslog.core.database.common.getFleetCollectionRef
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AircraftManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : AircraftManager {


  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> = try {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return Result.failure(
      Exception(
        "Fleet reference is null"
      )
    )
    val aircraftRef = getAircraftRefOrCreateNew(fleetRef, aircraft)
    val aircraftWithId = if (aircraft.id.isEmpty()) aircraft.copy(id = aircraftRef.id) else aircraft
    val data = mapOf(AIRCRAFT_INFO_BLOB to Blob.fromBytes(Aircraft.ADAPTER.encode(aircraftWithId)))

    // Use SetOptions.merge() to only update this field
    aircraftRef.set(data, SetOptions.merge()).await()
    logger.d { "Aircraft updated successfully, new id is ${aircraftWithId.id}" }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error updating aircraft" }

    Result.failure(e)
  }

  override fun loadAircraft(id: String): Flow<Aircraft?> = callbackFlow {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth)
    if (fleetRef == null) {
      trySend(null)
      close(Exception("Fleet reference is null"))
      return@callbackFlow
    }

    val docRef = fleetRef.document(id)
    val listener = docRef.addSnapshotListener { snapshot, e ->
      if (e != null) {
        logger.w(e) { "Listen failed for aircraft $id" }
        close(e)
        return@addSnapshotListener
      }

      if (snapshot != null && snapshot.exists()) {
        val blob = snapshot.get(AIRCRAFT_INFO_BLOB) as? Blob
        if (blob != null) {
          try {
            val aircraft = Aircraft.ADAPTER.decode(blob.toBytes())
            trySend(aircraft)
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse aircraft $id" }
            // Don't close, maybe next update fixes it? Or close?
            // Usually if parse fails, it's bad data.
            trySend(null)
          }
        } else {
          trySend(null)
        }
      } else {
        trySend(null)
      }
    }

    awaitClose { listener.remove() }
  }

  override suspend fun deleteAircraft(id: String): Result<Boolean> = try {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return Result.failure(
      Exception("Fleet reference is null")
    )
    fleetRef.document(id).delete().await()
    logger.d { "Aircraft $id deleted successfully." }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error deleting aircraft $id" }
    Result.failure(e)
  }


  private fun getAircraftRefOrCreateNew(
    fleetRef: CollectionReference,
    aircraft: Aircraft
  ): DocumentReference = if (aircraft.id.isEmpty()) {
    fleetRef.document()
  } else {
    fleetRef.document(aircraft.id)
  }

  companion object {
    private val logger = Logger.withTag("AircraftManagerImpl")
  }
}