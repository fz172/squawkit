package dev.fanfly.wingslog.aircraft.manager.impl

import com.google.common.flogger.FluentLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.copy
import dev.fanfly.wingslog.common.database.AIRCRAFT_INFO_BLOB
import dev.fanfly.wingslog.common.database.getFleetCollectionRef
import dev.fanfly.wingslog.aircraft.manager.AircraftManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AircraftManagerImpl @Inject internal constructor(
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
    val aircraftWithId = aircraft.copy {
      id = aircraftRef.id
    }
    val data = mapOf(AIRCRAFT_INFO_BLOB to Blob.fromBytes(aircraftWithId.toByteArray()))

    // Use SetOptions.merge() to only update this field
    aircraftRef.set(data, SetOptions.merge()).await()
    logger.atFine().log("Aircraft updated successfully.")
    Result.success(true)
  } catch (e: Exception) {
    logger.atWarning().withCause(e).log("Error updating aircraft")

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
        logger.atWarning().withCause(e).log("Listen failed for aircraft $id")
        close(e)
        return@addSnapshotListener
      }

      if (snapshot != null && snapshot.exists()) {
        val blob = snapshot.get(AIRCRAFT_INFO_BLOB) as? Blob
        if (blob != null) {
          try {
            val aircraft = Aircraft.parseFrom(blob.toBytes())
            trySend(aircraft)
          } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to parse aircraft $id")
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


  private fun getAircraftRefOrCreateNew(
    fleetRef: CollectionReference,
    aircraft: Aircraft
  ): DocumentReference = if (aircraft.id.isEmpty()) {
    fleetRef.document()
  } else {
    fleetRef.document(aircraft.id)
  }

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()

  }
}