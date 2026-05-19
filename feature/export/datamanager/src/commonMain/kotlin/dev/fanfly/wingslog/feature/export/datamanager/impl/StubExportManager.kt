package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class StubExportManager : ExportManager {
  override fun exportLogs(request: ExportRequest): Flow<ExportProgress> =
    flowOf(ExportProgress.Error("Logbook export is not implemented yet."))
}
