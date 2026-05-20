package dev.fanfly.wingslog.feature.export.datamanager

/**
 * User-selected export scope and filters.
 */
data class ExportRequest(
  val aircraftIds: List<String>,
  val dateRange: ExportDateRange,
  val includeOpenSquawks: Boolean,
  /** Report documents to write inside the ZIP. Attachments and README are always included. */
  val formats: Set<ExportFormat> = ExportFormat.ALL,
)
