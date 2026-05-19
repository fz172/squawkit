package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * On-device export manager that writes selected aircraft snapshots to a ZIP archive.
 */
class LogbookExportManager(
  private val aggregator: LogbookExportAggregator,
  private val archiveBuilder: LogbookExportArchiveBuilder,
  private val zipFileWriter: ZipFileWriter,
  private val exportFileStore: ExportFileStore,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ExportManager {

  override fun exportLogs(request: ExportRequest): Flow<ExportProgress> = flow {
    if (request.aircraftIds.isEmpty()) {
      emit(ExportProgress.Error(""))
      return@flow
    }

    emit(ExportProgress.Running(step = "", percent = 10))
    val bundles = request.aircraftIds.mapIndexed { index, aircraftId ->
      emit(
        ExportProgress.Running(
          step = "",
          percent = 10 + ((index + 1) * 35 / request.aircraftIds.size),
        )
      )
      aggregator.collect(request, aircraftId)
    }

    emit(ExportProgress.Running(step = "", percent = 55))
    val generatedAt = clock.now().toLocalDateTime(timeZone)
    val entries = archiveBuilder.buildEntries(
      request = request,
      bundles = bundles,
      generatedAt = generatedAt,
      timeZone = timeZone,
    )

    emit(ExportProgress.Running(step = "", percent = 80))
    val zipBytes = zipFileWriter.write(entries)
    val fileName = archiveBuilder.fileName(bundles, generatedAt.date)

    emit(ExportProgress.Running(step = "", percent = 95))
    val saved = exportFileStore.writeZip(fileName, zipBytes)
    emit(
      ExportProgress.Success(
        filePath = saved.filePath,
        displayLocation = saved.filePath,
        sizeBytes = saved.sizeBytes,
        displayLocationKind = saved.displayLocationKind,
      )
    )
  }
}
