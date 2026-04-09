package dev.fanfly.wingslog.feature.technician.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.database.TECHNICIAN_INFO_BLOB
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getTechniciansCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class TechnicianManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : TechnicianManager {

  private val logger = Logger.withTag("TechnicianManagerImpl")

  override fun observeTechnicians(): Flow<List<Technician>> {
    val collectionRef = firestore.getTechniciansCollectionRef(firebaseAuth) ?: return emptyFlow()

    return collectionRef.snapshots.map { snapshot ->
      if (snapshot.documents.isEmpty()) {
        logger.w { "No technicians found, returning empty list" }
        return@map emptyList()
      }

      val result = mutableListOf<Technician>()
      for (document in snapshot.documents) {
        val blobBytes = document.getBlobAsBytes(TECHNICIAN_INFO_BLOB)
        if (blobBytes == null || blobBytes.isEmpty()) {
          logger.w { "Missing or empty technician info blob, skipping ${document.id}" }
          continue
        }

        try {
          val technician = Technician.ADAPTER.decode(blobBytes)
          result += technician
        } catch (e: Exception) {
          logger.e(e) { "Failed to decode technician" }
        }
      }
      result
    }.catch { e ->
      logger.w(e) { "Listen failed." }
      emit(emptyList())
    }
  }

  override fun loadTechnician(id: String): Flow<Technician?> {
    val collectionRef = firestore.getTechniciansCollectionRef(firebaseAuth) ?: return emptyFlow()

    return collectionRef.document(id).snapshots.map { snapshot ->
      if (snapshot.exists) {
        val blobBytes = snapshot.getBlobAsBytes(TECHNICIAN_INFO_BLOB)
        if (blobBytes != null) {
          try {
            Technician.ADAPTER.decode(blobBytes)
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse technician $id" }
            null
          }
        } else null
      } else {
        null
      }
    }
  }

  override suspend fun updateTechnician(technician: Technician): Result<Boolean> = try {
    val collectionRef = firestore.getTechniciansCollectionRef(firebaseAuth)
      ?: return Result.failure(Exception("Technicians reference is null"))
    
    val docRef = getRefOrCreateNew(collectionRef, technician)
    val updatedTechnician = if (technician.id.isEmpty()) technician.copy(id = docRef.id) else technician
    val data = mapOf(TECHNICIAN_INFO_BLOB to Technician.ADAPTER.encode(updatedTechnician))

    docRef.setEncoded(data, merge = true)
    logger.d { "Technician updated successfully, new id is ${updatedTechnician.id}" }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error updating technician" }
    Result.failure(e)
  }

  override suspend fun deleteTechnician(id: String): Result<Boolean> = try {
    val collectionRef = firestore.getTechniciansCollectionRef(firebaseAuth)
      ?: return Result.failure(Exception("Technicians reference is null"))
      
    collectionRef.document(id).delete()
    logger.d { "Technician $id deleted successfully." }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error deleting technician $id" }
    Result.failure(e)
  }

  private fun getRefOrCreateNew(
    collectionRef: CollectionReference,
    technician: Technician,
  ): DocumentReference = if (technician.id.isEmpty()) {
    collectionRef.document(generateRandomId())
  } else {
    collectionRef.document(technician.id)
  }
}
