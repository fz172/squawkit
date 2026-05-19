package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.datetime.LocalDate

/**
 * Inclusive date filter applied to timestamped export records.
 */
sealed interface ExportDateRange {
  data object AllTime : ExportDateRange
  data class LastNMonths(val months: Int) : ExportDateRange
  data class Custom(val start: LocalDate, val endInclusive: LocalDate) : ExportDateRange
}
