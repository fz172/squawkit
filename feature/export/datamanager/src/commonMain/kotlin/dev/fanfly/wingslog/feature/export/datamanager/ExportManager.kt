package dev.fanfly.wingslog.feature.export.datamanager

import dev.fanfly.wingslog.export.ExportRecord
import kotlinx.coroutines.flow.Flow

/**
 * Generates logbook export archives for the selected aircraft and options.
 */
interface ExportManager {
  /**
   * Starts a cold export flow for [request].
   *
   * Collection performs the work, emits progress, and finishes with exactly one terminal event
   * unless the collector is cancelled.
   */
  fun exportLogs(request: ExportRequest): Flow<ExportProgress>

  /**
   * Returns previously generated export archives, newest first.
   */
  suspend fun listExports(): List<ExportRecord>

  /**
   * Deletes the export referenced by [exportId]. Returns true when an archive was removed.
   */
  suspend fun deleteExport(exportId: String): Boolean

  /**
   * Retries automatic delivery for a previously failed export, reporting whether the email was
   * sent or why it failed.
   */
  suspend fun retryDelivery(exportId: String): ExportDeliveryOutcome

  /**
   * Sends a fresh delivery email for an already-uploaded export, reusing the archive in remote
   * storage. No local file is generated or uploaded. Reports whether the email was sent or why it
   * failed.
   */
  suspend fun resendDelivery(exportId: String): ExportDeliveryOutcome

  /**
   * Downloads a remote-only export's archive to this device and records the local copy, so it can
   * be shared from the device afterward. Returns true when a local file is present once finished
   * (including the no-op case where it was already on device).
   */
  suspend fun saveToDevice(exportId: String): Boolean
}
