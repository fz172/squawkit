package dev.fanfly.wingslog.feature.export.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordAircraft
import dev.fanfly.wingslog.export.ExportRecordDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryOutcome
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
class ExportManagerImpl(
  private val aggregator: LogbookExportAggregator,
  private val attachmentExportResolver: AttachmentExportResolver,
  private val archiveBuilder: LogbookExportArchiveBuilder,
  private val zipFileWriter: ZipFileWriter,
  private val exportFileStore: ExportFileStore,
  private val remoteRepository: ExportHistoryRemoteRepository,
  private val deliveryBackend: ExportDeliveryBackend,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ExportManager {

  private val log = Logger.withTag("ExportManagerImpl")

  override fun exportLogs(request: ExportRequest): Flow<ExportProgress> = flow {
    if (request.aircraftIds.isEmpty()) {
      emit(ExportProgress.Error(""))
      return@flow
    }

    emit(ExportProgress.Running(step = ExportProgressStep.COLLECTING_DATA, percent = 8))
    val bundles = request.aircraftIds.mapIndexed { index, aircraftId ->
      emit(
        ExportProgress.Running(
          step = ExportProgressStep.COLLECTING_DATA,
          percent = 8 + ((index + 1) * 24 / request.aircraftIds.size),
        )
      )
      aggregator.collect(request, aircraftId)
    }
    val attachmentManifests = bundles.associate { bundle ->
      bundle.aircraft.id to attachmentExportResolver.resolve(bundle)
    }

    emit(ExportProgress.Running(step = ExportProgressStep.BUILDING_ARCHIVE, percent = 42))
    val generatedAt = clock.now().toLocalDateTime(timeZone)
    val entries = archiveBuilder.buildEntries(
      request = request,
      bundles = bundles,
      attachmentManifests = attachmentManifests,
      generatedAt = generatedAt,
      timeZone = timeZone,
    )

    emit(ExportProgress.Running(step = ExportProgressStep.COMPRESSING_ARCHIVE, percent = 60))
    val zipBytes = zipFileWriter.write(entries)
    val fileName = archiveBuilder.fileName(bundles, generatedAt.date)

    emit(ExportProgress.Running(step = ExportProgressStep.SAVING_FILE, percent = 74))
    val saved = exportFileStore.writeZip(fileName, zipBytes)
    // Persist the full scope so export history can rediscover it without parsing the file name.
    val localRecord = buildRecord(
      request = request,
      bundles = bundles,
      saved = saved,
      createdAtEpochMillis = clock.now().toEpochMilliseconds(),
    )
    exportFileStore.saveRecord(localRecord)
    emit(ExportProgress.Running(step = ExportProgressStep.UPLOADING_ARCHIVE, percent = 86))
    val remoteRecord = remoteRepository.uploadAndSync(localRecord, zipBytes)
    if (remoteRecord.remote_archive_ref.isNotBlank() && remoteRecord.destination_email.isNotBlank()) {
      emit(ExportProgress.Running(step = ExportProgressStep.UPLOADING_ARCHIVE, percent = 95))
    }
    val finalRecord = remoteRecord.requestDeliveryIfEligible()
    // Keep the on-device file after delivery so every export stays available both locally and in
    // the cloud. The persisted local record carries the remote ref + delivery state once synced.
    if (finalRecord != localRecord) {
      exportFileStore.saveRecord(finalRecord)
    }
    emit(
      ExportProgress.Success(
        filePath = saved.filePath,
        fileName = saved.fileName,
        // Left blank so the UI renders the localized label from displayLocationKind.
        displayLocation = "",
        sizeBytes = saved.sizeBytes,
        displayLocationKind = saved.displayLocationKind,
        persistedDeliveryState = finalRecord.persisted_delivery_state,
        deliveryFailureMessage = finalRecord.delivery_failure_message,
      )
    )
  }

  override suspend fun listExports(): List<ExportRecord> =
    ExportRecordMerge.merge(
      local = exportFileStore.listExports(),
      remote = remoteRepository.listRemoteRecords(),
    )

  override suspend fun deleteExport(exportId: String): Boolean {
    val localRecord = exportFileStore.listExports().firstOrNull { it.export_id == exportId }
    val remoteRecord = remoteRepository.listRemoteRecords().firstOrNull { it.export_id == exportId }

    if (remoteRecord != null && !remoteRepository.deleteExport(exportId, remoteRecord.remote_archive_ref)) {
      return false
    }

    return when {
      localRecord != null -> exportFileStore.deleteExport(exportId)
      remoteRecord != null -> true
      else -> false
    }
  }

  override suspend fun retryDelivery(exportId: String): ExportDeliveryOutcome {
    val record = listExports().firstOrNull { it.export_id == exportId }
      ?: return ExportDeliveryOutcome.Failed()
    if (record.persisted_delivery_state != ExportDeliveryStates.FAILED) return ExportDeliveryOutcome.Failed()
    if (record.remote_archive_ref.isBlank() || record.destination_email.isBlank()) {
      return ExportDeliveryOutcome.Failed()
    }
    return runCatching {
      deliveryBackend.requestExportDelivery(exportId).toOutcome()
    }.getOrElse { error ->
      log.w(error) { "export delivery retry failed for $exportId" }
      ExportDeliveryOutcome.Failed(error.message.orEmpty())
    }
  }

  override suspend fun resendDelivery(exportId: String): ExportDeliveryOutcome {
    val record =
      listExports().firstOrNull { it.export_id == exportId } ?: return ExportDeliveryOutcome.Failed()
    // Reuse the archive already in remote storage; never regenerate or re-upload a local file.
    if (record.remote_archive_ref.isBlank() || record.destination_email.isBlank()) {
      return ExportDeliveryOutcome.Failed()
    }
    return runCatching {
      // forceResend re-sends an already-delivered export, subject to the server-side cooldown.
      deliveryBackend.requestExportDelivery(exportId, forceResend = true).toOutcome()
    }.getOrElse { error ->
      log.w(error) { "export delivery resend failed for $exportId" }
      ExportDeliveryOutcome.Failed(error.message.orEmpty())
    }
  }

  /**
   * Maps the backend disposition to a user-facing outcome. We key off [requestOutcome], not [persistedDeliveryState]:
   * a throttled resend still reports persistedDeliveryState=SENT (from the earlier send), so relying on the
   * state alone would falsely claim a fresh email went out.
   */
  private fun ExportDeliveryResult.toOutcome(): ExportDeliveryOutcome = when (requestOutcome) {
    ExportRequestOutcomes.SENT -> ExportDeliveryOutcome.Sent
    ExportRequestOutcomes.RESEND_THROTTLED,
    ExportRequestOutcomes.ALREADY_SENT -> ExportDeliveryOutcome.Throttled
    ExportRequestOutcomes.IN_PROGRESS -> ExportDeliveryOutcome.InProgress
    ExportRequestOutcomes.FAILED -> ExportDeliveryOutcome.Failed(deliveryFailureMessage)
    // Unknown/older responses: fall back to the delivery state.
    else ->
      if (persistedDeliveryState == ExportDeliveryStates.FAILED) {
        ExportDeliveryOutcome.Failed(deliveryFailureMessage)
      } else {
        ExportDeliveryOutcome.Sent
      }
  }

  override suspend fun saveToDevice(exportId: String): Boolean {
    // Already on device (local record carries a file path)? Nothing to download.
    val local = exportFileStore.listExports().firstOrNull { it.export_id == exportId }
    if (local != null && local.file_path.isNotBlank()) return true

    val remote =
      remoteRepository.listRemoteRecords().firstOrNull { it.export_id == exportId } ?: return false
    if (remote.remote_archive_ref.isBlank()) return false

    val bytes = remoteRepository.downloadArchive(remote.remote_archive_ref) ?: return false
    val saved = exportFileStore.writeZip(remote.file_name, bytes)
    // Persist a local record so history shows it as on-device and the share sheet has a real file.
    // Merge keeps the remote delivery/archive fields, so the cloud copy stays referenced.
    exportFileStore.saveRecord(
      remote.copy(
        file_path = saved.filePath,
        size_bytes = saved.sizeBytes,
        display_location = saved.displayLocationKind.name,
      )
    )
    return true
  }

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
    destination_email = request.destinationEmail.orEmpty(),
    destination_email_source = request.destinationEmailSource.orEmpty(),
    persisted_delivery_state = ExportDeliveryStates.NOT_REQUESTED,
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

  private suspend fun ExportRecord.requestDeliveryIfEligible(): ExportRecord {
    if (remote_archive_ref.isBlank() || destination_email.isBlank()) return this
    return runCatching {
      val delivery = deliveryBackend.requestExportDelivery(export_id)
      copy(
        persisted_delivery_state = delivery.persistedDeliveryState,
        delivery_sent_at_epoch_millis = delivery.deliverySentAtEpochMillis,
        delivery_failure_code = delivery.deliveryFailureCode,
        delivery_failure_message = delivery.deliveryFailureMessage,
      )
    }.getOrElse { error ->
      log.w(error) { "export delivery request failed for $export_id" }
      this
    }
  }
}
