package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.datetime.LocalDate

/**
 * User-selected export scope and filters.
 */
data class ExportRequest(
  val aircraftIds: List<String>,
  val dateRange: ExportDateRange,
  val includeOpenSquawks: Boolean,
)

/**
 * Inclusive date filter applied to timestamped export records.
 */
sealed interface ExportDateRange {
  data object AllTime : ExportDateRange
  data class LastNMonths(val months: Int) : ExportDateRange
  data class Custom(val start: LocalDate, val endInclusive: LocalDate) : ExportDateRange
}

/**
 * Streamed export status emitted by [ExportManager].
 */
sealed interface ExportProgress {
  data class Running(val step: String, val percent: Int) : ExportProgress

  /**
   * Completed archive metadata. UI should prefer [displayLocationKind] for localized labels.
   */
  data class Success(
    val filePath: String,
    val displayLocation: String,
    val sizeBytes: Long,
    val displayLocationKind: ExportDisplayLocation = ExportDisplayLocation.UNKNOWN,
  ) : ExportProgress

  data class Error(val message: String, val cause: Throwable? = null) : ExportProgress
}

/**
 * Stable platform destination identifier for localized success copy.
 */
enum class ExportDisplayLocation {
  UNKNOWN,
  DOWNLOADS_HOPPLY,
  FILES_HOPPLY,
}
