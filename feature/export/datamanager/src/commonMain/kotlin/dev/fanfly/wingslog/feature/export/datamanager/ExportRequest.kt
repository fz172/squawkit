package dev.fanfly.wingslog.feature.export.datamanager

/**
 * User-selected export scope and filters.
 */
data class ExportRequest(
  val aircraftIds: List<String>,
  val dateRange: ExportDateRange,
  val includeOpenSquawks: Boolean,
)
