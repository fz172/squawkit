package dev.fanfly.wingslog.feature.export.datamanager.impl

import androidx.work.Data
import androidx.work.WorkInfo
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportJob
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import kotlinx.datetime.LocalDate

/**
 * How an export request and its progress travel through WorkManager `Data`, so the job can be
 * rebuilt from a `WorkInfo` alone — after process death as well as while the screen is closed.
 *
 * `WorkInfo` exposes progress and output but not input, so the worker echoes the request into
 * every progress update and into its output; nothing needs a store of its own. Plain fields rather
 * than JSON: `Data` already handles string arrays, and the whole request is a few hundred bytes
 * against its 10 KB cap.
 */
internal object ExportWorkData {
  private const val THING_IDS = "thing_ids"
  private const val RANGE_KIND = "range_kind"
  private const val RANGE_MONTHS = "range_months"
  private const val RANGE_START = "range_start"
  private const val RANGE_END = "range_end"
  private const val INCLUDE_OPEN_SQUAWKS = "include_open_squawks"
  private const val FORMATS = "formats"
  private const val DESTINATION_EMAIL = "destination_email"
  private const val DESTINATION_EMAIL_SOURCE = "destination_email_source"

  private const val OUTCOME = "outcome"
  private const val OUTCOME_RUNNING = "running"
  private const val OUTCOME_SUCCESS = "success"
  private const val OUTCOME_ERROR = "error"
  private const val STEP = "step"
  private const val PERCENT = "percent"
  private const val EXPORT_ID = "export_id"
  private const val FILE_PATH = "file_path"
  private const val FILE_NAME = "file_name"
  private const val SIZE_BYTES = "size_bytes"
  private const val DISPLAY_LOCATION = "display_location"
  private const val DELIVERY_STATE = "delivery_state"
  private const val DELIVERY_FAILURE = "delivery_failure"
  private const val MESSAGE = "message"

  fun encodeRequest(request: ExportRequest): Data = Data.Builder()
    .putRequest(request)
    .build()

  fun encode(request: ExportRequest, progress: ExportProgress): Data = Data.Builder()
    .putRequest(request)
    .putProgress(progress)
    .build()

  fun decodeRequest(data: Data): ExportRequest? {
    val thingIds = data.getStringArray(THING_IDS)?.toList() ?: return null
    val dateRange = when (data.getString(RANGE_KIND)) {
      "ALL_TIME" -> ExportDateRange.AllTime
      "LAST_N_MONTHS" -> ExportDateRange.LastNMonths(data.getInt(RANGE_MONTHS, 12))
      "CUSTOM" -> ExportDateRange.Custom(
        start = data.getString(RANGE_START)?.let(LocalDate::parse) ?: return null,
        endInclusive = data.getString(RANGE_END)?.let(LocalDate::parse) ?: return null,
      )

      else -> return null
    }
    val formats = data.getStringArray(FORMATS)
      ?.mapNotNull { name -> ExportFormat.entries.firstOrNull { it.name == name } }
      ?.toSet()
      ?: return null
    return ExportRequest(
      thingIds = thingIds,
      dateRange = dateRange,
      includeOpenSquawks = data.getBoolean(INCLUDE_OPEN_SQUAWKS, true),
      formats = formats,
      destinationEmail = data.getString(DESTINATION_EMAIL),
      destinationEmailSource = data.getString(DESTINATION_EMAIL_SOURCE),
    )
  }

  fun decodeProgress(data: Data): ExportProgress? = when (data.getString(OUTCOME)) {
    OUTCOME_RUNNING -> ExportProgress.Running(
      step = data.getString(STEP)
        ?.let { name -> ExportProgressStep.entries.firstOrNull { it.name == name } }
        ?: ExportProgressStep.COLLECTING_DATA,
      percent = data.getInt(PERCENT, 0),
    )

    OUTCOME_SUCCESS -> ExportProgress.Success(
      exportId = data.getString(EXPORT_ID).orEmpty(),
      filePath = data.getString(FILE_PATH).orEmpty(),
      fileName = data.getString(FILE_NAME).orEmpty(),
      displayLocation = "",
      sizeBytes = data.getLong(SIZE_BYTES, 0L),
      displayLocationKind = data.getString(DISPLAY_LOCATION)
        ?.let { name -> ExportDisplayLocation.entries.firstOrNull { it.name == name } }
        ?: ExportDisplayLocation.UNKNOWN,
      persistedDeliveryState = data.getString(DELIVERY_STATE).orEmpty(),
      deliveryFailureMessage = data.getString(DELIVERY_FAILURE).orEmpty(),
    )

    OUTCOME_ERROR -> ExportProgress.Error(data.getString(MESSAGE).orEmpty())
    else -> null
  }

  /**
   * The job a `WorkInfo` describes, or null when there is none to show: cancelled work, or work
   * that has not started yet (no progress echo) and whose request the caller cannot supply.
   */
  fun toJob(info: WorkInfo, pendingRequest: ExportRequest?): ExportJob? {
    val id = info.id.toString()
    return when (info.state) {
      WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
        val request = decodeRequest(info.progress) ?: pendingRequest ?: return null
        val progress = decodeProgress(info.progress) as? ExportProgress.Running
          ?: ExportProgress.Running(ExportProgressStep.COLLECTING_DATA, 0)
        ExportJob(id, request, progress)
      }

      WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED -> {
        val request = decodeRequest(info.outputData) ?: return null
        val progress = decodeProgress(info.outputData) ?: return null
        ExportJob(id, request, progress)
      }

      WorkInfo.State.CANCELLED -> null
    }
  }

  private fun Data.Builder.putRequest(request: ExportRequest): Data.Builder = apply {
    putStringArray(THING_IDS, request.thingIds.toTypedArray())
    when (val range = request.dateRange) {
      ExportDateRange.AllTime -> putString(RANGE_KIND, "ALL_TIME")
      is ExportDateRange.LastNMonths -> {
        putString(RANGE_KIND, "LAST_N_MONTHS")
        putInt(RANGE_MONTHS, range.months)
      }

      is ExportDateRange.Custom -> {
        putString(RANGE_KIND, "CUSTOM")
        putString(RANGE_START, range.start.toString())
        putString(RANGE_END, range.endInclusive.toString())
      }
    }
    putBoolean(INCLUDE_OPEN_SQUAWKS, request.includeOpenSquawks)
    putStringArray(FORMATS, request.formats.map { it.name }.toTypedArray())
    putString(DESTINATION_EMAIL, request.destinationEmail)
    putString(DESTINATION_EMAIL_SOURCE, request.destinationEmailSource)
  }

  private fun Data.Builder.putProgress(progress: ExportProgress): Data.Builder = apply {
    when (progress) {
      is ExportProgress.Running -> {
        putString(OUTCOME, OUTCOME_RUNNING)
        putString(STEP, progress.step.name)
        putInt(PERCENT, progress.percent)
      }

      is ExportProgress.Success -> {
        putString(OUTCOME, OUTCOME_SUCCESS)
        putString(EXPORT_ID, progress.exportId)
        putString(FILE_PATH, progress.filePath)
        putString(FILE_NAME, progress.fileName)
        putLong(SIZE_BYTES, progress.sizeBytes)
        putString(DISPLAY_LOCATION, progress.displayLocationKind.name)
        putString(DELIVERY_STATE, progress.persistedDeliveryState)
        putString(DELIVERY_FAILURE, progress.deliveryFailureMessage)
      }

      is ExportProgress.Error -> {
        putString(OUTCOME, OUTCOME_ERROR)
        putString(MESSAGE, progress.message)
      }
    }
  }
}
