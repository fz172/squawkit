package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.datetime.LocalDate

data class ExportRequest(
  val aircraftIds: List<String>,
  val dateRange: ExportDateRange,
  val includeOpenSquawks: Boolean,
)

sealed interface ExportDateRange {
  data object AllTime : ExportDateRange
  data class LastNMonths(val months: Int) : ExportDateRange
  data class Custom(val start: LocalDate, val endInclusive: LocalDate) : ExportDateRange
}

sealed interface ExportProgress {
  data class Running(val step: String, val percent: Int) : ExportProgress
  data class Success(
    val filePath: String,
    val displayLocation: String,
    val sizeBytes: Long,
    val displayLocationKind: ExportDisplayLocation = ExportDisplayLocation.UNKNOWN,
  ) : ExportProgress

  data class Error(val message: String, val cause: Throwable? = null) : ExportProgress
}

enum class ExportDisplayLocation {
  UNKNOWN,
  DOWNLOADS_HOPPLY,
  FILES_HOPPLY,
}
