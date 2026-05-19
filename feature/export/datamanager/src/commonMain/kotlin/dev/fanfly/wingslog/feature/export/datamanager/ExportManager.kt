package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.coroutines.flow.Flow

interface ExportManager {
  fun exportLogs(request: ExportRequest): Flow<ExportProgress>
}
