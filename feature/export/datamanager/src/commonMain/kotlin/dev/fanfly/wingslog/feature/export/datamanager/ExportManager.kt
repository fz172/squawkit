package dev.fanfly.wingslog.feature.export.datamanager

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
}
