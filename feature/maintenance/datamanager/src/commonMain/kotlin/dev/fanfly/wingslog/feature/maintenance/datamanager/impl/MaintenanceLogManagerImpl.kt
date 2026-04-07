package dev.fanfly.wingslog.feature.maintenance.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import dev.fanfly.wingslog.core.database.generateRandomId
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getFleetCollectionRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.feature.maintenance.datamanager.MaintenanceLogManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.where
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

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

  override fun observeMaintenanceOverview(aircraftId: String): Flow<MaintenanceOverview?> {
    val overviewRef = getOverviewDocumentRef(aircraftId) ?: return flowOf(null)
    return overviewRef.snapshots.map { snapshot ->
      if (!snapshot.exists) {
        // We don't refresh here to avoid side effects in a flow,
        // the ViewModel or first load should handle the initial refresh.
        null
      } else {
        val blobBytes = snapshot.getBlobAsBytes(OVERVIEW_INFO_BLOB)
        if (blobBytes != null) {
          try {
            MaintenanceOverview.ADAPTER.decode(blobBytes)
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse maintenance overview for $aircraftId" }
            null
          }
        } else null
      }
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
    refreshOverview(aircraftId)
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
    refreshOverview(aircraftId)
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
    refreshOverview(aircraftId)
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
    docRef: DocumentReference,
    log: MaintenanceLog,
  ) {
    val data = mutableMapOf(
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

    if (log.engine_hour > 0.0) {
      data[ENGINE_HOUR_FIELD] = log.engine_hour
    }

    docRef.setEncoded(data, merge = true)
  }

  private fun getLogsCollectionRef(aircraftId: String): CollectionReference? {
    return firestore.getFleetCollectionRef(firebaseAuth)?.document(aircraftId)
      ?.collection(MAINTENANCE_LOGS_COLLECTION)
  }

  private fun getOverviewDocumentRef(aircraftId: String): DocumentReference? {
    return firestore.getFleetCollectionRef(firebaseAuth)?.document(aircraftId)
      ?.collection(MAINTENANCE_LOGS_COLLECTION)?.document(OVERVIEW_DOCUMENT)
  }

  private suspend fun refreshOverview(aircraftId: String) {
    val logsRef = getLogsCollectionRef(aircraftId) ?: return
    val overviewRef = getOverviewDocumentRef(aircraftId) ?: return

    try {
      // For now, we load all logs to compute the summary.
      // In a production app with thousands of logs, you'd use a Cloud Function to update this incrementally.
      val snapshot = logsRef.get()
      val logs = snapshot.documents.mapNotNull { doc ->
        if (doc.id == OVERVIEW_DOCUMENT) return@mapNotNull null
        val blobBytes = doc.getBlobAsBytes(LOG_INFO_BLOB)
        if (blobBytes != null) {
          try {
            MaintenanceLog.ADAPTER.decode(blobBytes)
          } catch (e: Exception) {
            null
          }
        } else null
      }

      val overview = MaintenanceOverview(
        aircraft_id = aircraftId,
        total_log_count = logs.size,
        airframe_log_count = logs.count { it.component_type == MaintenanceLog.ComponentType.AIRFRAME },
        engine_log_count = logs.count { it.component_type == MaintenanceLog.ComponentType.ENGINE },
        propeller_log_count = logs.count { it.component_type == MaintenanceLog.ComponentType.PROPELLER },
        current_airframe_time = logs.filter { it.airframe_time > 0.0 }
          .maxOfOrNull { it.airframe_time } ?: 0.0,
        current_engine_time = logs.filter { it.engine_hour > 0.0 }.maxOfOrNull { it.engine_hour }
          ?: 0.0,
        current_propeller_time = logs.filter { it.prop_time > 0.0 }.maxOfOrNull { it.prop_time }
          ?: 0.0
      )

      val data = mapOf(OVERVIEW_INFO_BLOB to MaintenanceOverview.ADAPTER.encode(overview))
      overviewRef.setEncoded(data, merge = true)
    } catch (e: Exception) {
      logger.w(e) { "Failed to refresh overview for $aircraftId" }
    }
  }

  companion object {
    private val logger = Logger.withTag("MaintenanceLogManagerImpl")
    private const val MAINTENANCE_LOGS_COLLECTION = "maintenance_logs"
    private const val OVERVIEW_DOCUMENT = "maintenance_overview"
    private const val LOG_INFO_BLOB = "log_info_blob"
    private const val OVERVIEW_INFO_BLOB = "overview_info_blob"

    private const val TIMESTAMP_FIELD = "timestamp"
    private const val COMPONENT_TYPE_FIELD = "component_type"
    private const val TECHNICIAN_ID_FIELD = "technician_id"
    private const val INSPECTION_IDS_FIELD = "inspection_ids"
    private const val COMPONENT_SERIAL_FIELD = "component_serial"
    private const val ENGINE_HOUR_FIELD = "engine_hour"
  }
}
