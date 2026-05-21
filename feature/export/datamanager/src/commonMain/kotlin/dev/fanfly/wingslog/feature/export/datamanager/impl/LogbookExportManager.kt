package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordAircraft
import dev.fanfly.wingslog.export.ExportRecordDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
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
  private val attachmentExportResolver: AttachmentExportResolver,
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

    emit(ExportProgress.Running(step = ExportProgressStep.COLLECTING_DATA, percent = 10))
    val bundles = request.aircraftIds.mapIndexed { index, aircraftId ->
      emit(
        ExportProgress.Running(
          step = ExportProgressStep.COLLECTING_DATA,
          percent = 10 + ((index + 1) * 35 / request.aircraftIds.size),
        )
      )
      aggregator.collect(request, aircraftId)
    }
    val attachmentManifests = bundles.associate { bundle ->
      bundle.aircraft.id to attachmentExportResolver.resolve(bundle)
    }

    emit(ExportProgress.Running(step = ExportProgressStep.BUILDING_ARCHIVE, percent = 55))
    val generatedAt = clock.now().toLocalDateTime(timeZone)
    val entries = archiveBuilder.buildEntries(
      request = request,
      bundles = bundles,
      attachmentManifests = attachmentManifests,
      generatedAt = generatedAt,
      timeZone = timeZone,
    )

    emit(ExportProgress.Running(step = ExportProgressStep.COMPRESSING_ARCHIVE, percent = 80))
    val zipBytes = zipFileWriter.write(entries)
    val fileName = archiveBuilder.fileName(bundles, generatedAt.date)

    emit(ExportProgress.Running(step = ExportProgressStep.SAVING_FILE, percent = 95))
    val saved = exportFileStore.writeZip(fileName, zipBytes)
    // Persist the full scope so export history can rediscover it without parsing the file name.
    exportFileStore.saveRecord(
      buildRecord(request, bundles, saved, createdAtEpochMillis = clock.now().toEpochMilliseconds()),
    )
    emit(
      ExportProgress.Success(
        filePath = saved.filePath,
        fileName = saved.fileName,
        // Left blank so the UI renders the localized label from displayLocationKind.
        displayLocation = "",
        sizeBytes = saved.sizeBytes,
        displayLocationKind = saved.displayLocationKind,
      )
    )
  }

  override suspend fun listExports(): List<ExportRecord> = exportFileStore.listExports()

  override suspend fun deleteExport(exportId: String): Boolean =
    exportFileStore.deleteExport(exportId)

  private fun buildRecord(
    request: ExportRequest,
    bundles: List<AircraftBundle>,
    saved: ExportedFile,
    createdAtEpochMillis: Long,
  ): ExportRecord = ExportRecord(
    export_id = generateRandomId(),
    file_path = saved.filePath,
    file_name = saved.fileName,
    size_bytes = saved.sizeBytes,
    created_at_epoch_millis = createdAtEpochMillis,
    display_location = saved.displayLocationKind.name,
    formats = ExportFormat.entries.filter { it in request.formats }.map { it.name },
    date_range = request.dateRange.toRecordDateRange(),
    aircraft = bundles.map { bundle ->
      ExportRecordAircraft(
        tail_number = bundle.aircraft.tail_number,
        make_model = listOf(bundle.aircraft.make, bundle.aircraft.model)
          .filter { it.isNotBlank() }
          .joinToString(" "),
      )
    },
  )

  private fun ExportDateRange.toRecordDateRange(): ExportRecordDateRange = when (this) {
    ExportDateRange.AllTime -> ExportRecordDateRange(kind = "ALL_TIME")
    is ExportDateRange.LastNMonths -> ExportRecordDateRange(kind = "LAST_N_MONTHS", months = months)
    is ExportDateRange.Custom -> ExportRecordDateRange(
      kind = "CUSTOM",
      custom_start = start.toString(),
      custom_end = endInclusive.toString(),
    )
  }
}
