package dev.fanfly.wingslog.feature.fleet.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.database.AIRCRAFT_INFO_BLOB
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FleetManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : FleetManager {

  private val logger = Logger.withTag("FleetManagerImpl")

  override fun observeFleetDashboard(): Flow<List<Aircraft>> {
    val fleetCollectionRef =
      firestore.getFleetCollectionRef(firebaseAuth) ?: return emptyFlow()

    return fleetCollectionRef.snapshots.map { snapshot ->
      if (snapshot.documents.isEmpty()) {
        logger.w { "No fleet data, returning empty" }
        return@map emptyList()
      }
      logger.w { "Fleet data size {${snapshot.documents.size}}" }

      val result = mutableListOf<Aircraft>()
      for (document in snapshot.documents) {
        // Wire 5.x uses camelCase for properties
        val blobBytes = document.getBlobAsBytes(AIRCRAFT_INFO_BLOB)
        if (blobBytes == null || blobBytes.isEmpty()) {
          logger.w { "Missing or empty aircraft info blob, skipping ${document.id}" }
          continue
        }

        try {
          val aircraft = Aircraft.ADAPTER.decode(blobBytes)
          result += aircraft
          logger.i { "Recovered Aircraft: ${aircraft.tail_number} - ${aircraft.model}" }
        } catch (e: Exception) {
          logger.e(e) { "Failed to decode aircraft" }
        }
      }
      result
    }.catch { e ->
      logger.w(e) { "Listen failed." }
      emit(emptyList())
    }
  }

  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> = try {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth) ?: return Result.failure(
      Exception("Fleet reference is null")
    )
    val aircraftRef = getAircraftRefOrCreateNew(fleetRef, aircraft)
    val aircraftWithId = if (aircraft.id.isEmpty()) aircraft.copy(id = aircraftRef.id) else aircraft
    val data = mapOf(AIRCRAFT_INFO_BLOB to Aircraft.ADAPTER.encode(aircraftWithId))

    aircraftRef.setEncoded(data, merge = true)
    logger.d { "Aircraft updated successfully, new id is ${aircraftWithId.id}, data is $data" }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error updating aircraft" }
    Result.failure(e)
  }

  override fun loadAircraft(id: String): Flow<Aircraft?> {
    val fleetRef = firestore.getFleetCollectionRef(firebaseAuth)
      ?: return flowOf(null)

    val docRef = fleetRef.document(id)
    return docRef.snapshots.map { snapshot ->
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
    aircraft: Aircraft,
  ): DocumentReference = if (aircraft.id.isEmpty()) {
    fleetRef.document(generateRandomId())
  } else {
    fleetRef.document(aircraft.id)
  }
}
