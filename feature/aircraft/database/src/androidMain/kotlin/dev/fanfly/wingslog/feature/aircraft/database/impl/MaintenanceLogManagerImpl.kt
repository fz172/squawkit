package dev.fanfly.wingslog.feature.aircraft.database.impl

import co.touchlab.kermit.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.database.common.getFleetCollectionRef
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class MaintenanceLogManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : MaintenanceLogManager {

  override fun observeLogs(aircraftId: String): Flow<List<MaintenanceLog>> = callbackFlow {
    val logsRef = getLogsCollectionRef(aircraftId)
    if (logsRef == null) {
      trySend(emptyList())
      close(Exception("Logs reference is null (user likely not logged in)"))
      return@callbackFlow
    }

    val listener = logsRef
      .orderBy(TIMESTAMP_FIELD, com.google.firebase.firestore.Query.Direction.DESCENDING)
      .addSnapshotListener { snapshot, e ->
      if (e != null) {
        logger.w(e) { "Listen failed for logs of aircraft $aircraftId" }
        close(e)
        return@addSnapshotListener
      }

      val logs = mutableListOf<MaintenanceLog>()
      if (snapshot != null && !snapshot.isEmpty) {
        for (doc in snapshot.documents) {
          val blob = doc.getBlob(LOG_INFO_BLOB)
          if (blob != null) {
            try {
              val log = MaintenanceLog.ADAPTER.decode(blob.toBytes())
              // ID should match document ID, just in case
              logs.add(log)
            } catch (e: Exception) {
              logger.w(e) { "Failed to parse log ${doc.id}" }
            }
          }
        }
      }
      trySend(logs)
    }

    awaitClose { listener.remove() }
  }

  override suspend fun addLog(aircraftId: String, log: MaintenanceLog): Result<Boolean> = try {
    val logsRef = getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    
    // Generate an ID if needed, or let Firestore generate it.
    // However, since we store the ID inside the Proto, we should probably generate it first.
    val newDocRef = if (log.id.isEmpty()) logsRef.document() else logsRef.document(log.id)
    val finalLog = if (log.id.isEmpty()) log.copy(id = newDocRef.id) else log

    saveLog(newDocRef, finalLog)
    
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error adding log" }
    Result.failure(e)
  }

  override suspend fun updateLog(aircraftId: String, log: MaintenanceLog): Result<Boolean> = try {
    val logsRef = getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    val docRef = logsRef.document(log.id)
    
    saveLog(docRef, log)
    
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error updating log ${log.id}" }
    Result.failure(e)
  }

  override suspend fun deleteLog(aircraftId: String, logId: String): Result<Boolean> = try {
    val logsRef = getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    logsRef.document(logId).delete().await()
    logger.d { "Log $logId deleted successfully." }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error deleting log $logId" }
    Result.failure(e)
  }

  override suspend fun getRecentLogCount(aircraftId: String, days: Int): Result<Long> = try {
    val logsRef = getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))

    val cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
    // Convert to seconds and nanoseconds for Timestamp
    val cutoffTimestamp = com.google.firebase.Timestamp(cutoffMillis / 1000, ((cutoffMillis % 1000) * 1000000).toInt())

    val snapshot = logsRef
      .whereGreaterThan(TIMESTAMP_FIELD, cutoffTimestamp)
      .count()
      .get(AggregateSource.SERVER)
      .await()

    Result.success(snapshot.count)
  } catch (e: Exception) {
    logger.w(e) { "Error counting recent logs" }
    Result.failure(e)
  }

  private suspend fun saveLog(docRef: com.google.firebase.firestore.DocumentReference, log: MaintenanceLog) {
    // Map fields for querying
    val data = hashMapOf<String, Any>(
      LOG_INFO_BLOB to Blob.fromBytes(MaintenanceLog.ADAPTER.encode(log)),
      TIMESTAMP_FIELD to com.google.firebase.Timestamp(log.timestamp?.epochSecond ?: 0L, log.timestamp?.nano ?: 0),
      COMPONENT_TYPE_FIELD to log.component_type.name,
      TECHNICIAN_ID_FIELD to log.technician_id,
      INSPECTION_IDS_FIELD to log.inspection_ids
    )
    
    if (log.component_serial.isNotEmpty()) {
      data[COMPONENT_SERIAL_FIELD] = log.component_serial
    }
    
    if (log.tach_time > 0) {
      data[TACH_TIME_FIELD] = log.tach_time
    }

    docRef.set(data, SetOptions.merge()).await()
  }

  private fun getLogsCollectionRef(aircraftId: String): CollectionReference? {
    return firestore.getFleetCollectionRef(firebaseAuth)?.document(aircraftId)?.collection(MAINTENANCE_LOGS_COLLECTION)
  }

  companion object {
    private val logger = Logger.withTag("MaintenanceLogManagerImpl")
    private const val MAINTENANCE_LOGS_COLLECTION = "maintenance_logs"
    private const val LOG_INFO_BLOB = "log_info_blob"
    
    private const val TIMESTAMP_FIELD = "timestamp"
    private const val COMPONENT_TYPE_FIELD = "component_type"
    private const val TECHNICIAN_ID_FIELD = "technician_id"
    private const val INSPECTION_IDS_FIELD = "inspection_ids"
    private const val COMPONENT_SERIAL_FIELD = "component_serial"
    private const val TACH_TIME_FIELD = "tach_time"
  }
}
