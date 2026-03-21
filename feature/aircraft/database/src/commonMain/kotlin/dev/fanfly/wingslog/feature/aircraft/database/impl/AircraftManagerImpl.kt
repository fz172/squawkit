package dev.fanfly.wingslog.feature.aircraft.database.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.fanfly.wingslog.core.database.observeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AircraftManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : AircraftManager {

  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> = try {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return Result.failure(
      Exception("Fleet reference is null")
    )
    val aircraftRef = getAircraftRefOrCreateNew(fleetRef, aircraft)
    val aircraftWithId = if (aircraft.id.isEmpty()) aircraft.copy(id = aircraftRef.id) else aircraft
    val data = mapOf(AIRCRAFT_INFO_BLOB to Aircraft.ADAPTER.encode(aircraftWithId))

    aircraftRef.setEncoded(data, merge = true)
    logger.d { "Aircraft updated successfully, new id is ${aircraftWithId.id}" }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error updating aircraft" }
    Result.failure(e)
  }

  override fun loadAircraft(id: String): Flow<Aircraft?> {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth)
      ?: return kotlinx.coroutines.flow.flowOf(null)

    val docRef = fleetRef.document(id)
    return docRef.observeSnapshot().map { snapshot ->
      if (snapshot.exists) {
        val blobBytes = snapshot.getBlobAsBytes(AIRCRAFT_INFO_BLOB)
        if (blobBytes != null) {
          try {
            Aircraft.ADAPTER.decode(blobBytes)
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse aircraft $id" }
            null
          }
        } else {
          null
        }
      } else {
        null
      }
    }
  }

  override suspend fun deleteAircraft(id: String): Result<Boolean> = try {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return Result.failure(
      Exception("Fleet reference is null")
    )
    fleetRef.document(id).delete()
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
    fleetRef.document(generateRandomId())
  } else {
    fleetRef.document(aircraft.id)
  }

  companion object {
    private val logger = Logger.withTag("AircraftManagerImpl")
    private const val AIRCRAFT_INFO_BLOB = "aircraft_info_blob"
  }
}
