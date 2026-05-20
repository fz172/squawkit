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
   * Deletes the export referenced by [filePath]. Returns true when an archive was removed.
   */
  suspend fun deleteExport(filePath: String): Boolean
}
