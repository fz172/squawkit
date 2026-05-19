package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class StubExportManager : ExportManager {
  override fun exportLogs(request: ExportRequest): Flow<ExportProgress> =
    flow {
      emit(ExportProgress.Running(step = "", percent = 20))
      emit(ExportProgress.Running(step = "", percent = 100))
      emit(
        ExportProgress.Success(
          filePath = "",
          displayLocation = "",
          sizeBytes = 0L,
        )
      )
    }
}
