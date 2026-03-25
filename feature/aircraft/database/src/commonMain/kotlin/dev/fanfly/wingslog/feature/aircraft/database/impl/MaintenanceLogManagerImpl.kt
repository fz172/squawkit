package dev.fanfly.wingslog.feature.aircraft.database.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class MaintenanceLogManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : MaintenanceLogManager {

  override fun observeLogs(aircraftId: String): Flow<List<MaintenanceLog>> {
    val logsRef = getLogsCollectionRef(aircraftId)
      ?: return kotlinx.coroutines.flow.flowOf(emptyList())

    return logsRef.snapshots.map { snapshot ->
      val logs = mutableListOf<MaintenanceLog>()
      for (doc in snapshot.documents) {
        val blobBytes = doc.getBlobAsBytes(LOG_INFO_BLOB)
        if (blobBytes != null) {
          try {
            logs.add(MaintenanceLog.ADAPTER.decode(blobBytes))
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse log ${doc.id}" }
          }
        }
      }
      // Use epochSecond from Wire Instant for sorting
      logs.sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    }
  }

  override suspend fun addLog(aircraftId: String, log: MaintenanceLog): Result<Boolean> = try {
    val logsRef =
      getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))

    val newDocRef = if (log.id.isEmpty()) {
      logsRef.document(generateRandomId())
    } else {
      logsRef.document(log.id)
    }

    val finalLog = if (log.id.isEmpty()) log.copy(id = newDocRef.id) else log

    saveLog(newDocRef, finalLog)
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error adding log" }
    Result.failure(e)
  }

  override suspend fun updateLog(aircraftId: String, log: MaintenanceLog): Result<Boolean> = try {
    val logsRef =
      getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    val docRef = logsRef.document(log.id)
    saveLog(docRef, log)
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error updating log ${log.id}" }
    Result.failure(e)
  }

  override suspend fun deleteLog(aircraftId: String, logId: String): Result<Boolean> = try {
    val logsRef =
      getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))
    logsRef.document(logId).delete()
    logger.d { "Log $logId deleted successfully." }
    Result.success(true)
  } catch (e: Exception) {
    logger.w(e) { "Error deleting log $logId" }
    Result.failure(e)
  }

  override suspend fun getRecentLogCount(aircraftId: String, days: Int): Result<Long> = try {
    val logsRef =
      getLogsCollectionRef(aircraftId) ?: return Result.failure(Exception("User not logged in"))

    val cutoff = Clock.System.now().minus(days.days)
    val snapshot = logsRef.where(TIMESTAMP_FIELD, greaterThan = cutoff).get()
    Result.success(snapshot.documents.size.toLong())
  } catch (e: Exception) {
    logger.w(e) { "Error counting recent logs" }
    Result.failure(e)
  }

  private suspend fun saveLog(
    docRef: dev.gitlive.firebase.firestore.DocumentReference,
    log: MaintenanceLog
  ) {
    val data = mutableMapOf<String, Any>(
      LOG_INFO_BLOB to MaintenanceLog.ADAPTER.encode(log),
      TIMESTAMP_FIELD to (log.timestamp?.let {
        Instant.fromEpochSeconds(
          it.getEpochSecond(),
          it.getNano()
        )
      }
        ?: Instant.fromEpochSeconds(0L)),
      COMPONENT_TYPE_FIELD to log.component_type.name,
      TECHNICIAN_ID_FIELD to log.technician_id,
      INSPECTION_IDS_FIELD to log.inspection_ids
    )

    if (log.component_serial.isNotEmpty()) {
      data[COMPONENT_SERIAL_FIELD] = log.component_serial
    }

    if (log.tach_time > 0.0) {
      data[TACH_TIME_FIELD] = log.tach_time
    }

    docRef.setEncoded(data, merge = true)
  }

  private fun getLogsCollectionRef(aircraftId: String): CollectionReference? {
    return firestore.getFleetCollectionRef(firebaseAuth)?.document(aircraftId)
      ?.collection(MAINTENANCE_LOGS_COLLECTION)
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
