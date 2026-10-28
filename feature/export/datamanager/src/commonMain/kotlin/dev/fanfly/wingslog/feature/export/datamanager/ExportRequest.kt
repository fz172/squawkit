package dev.fanfly.wingslog.feature.export.datamanager

import dev.fanfly.wingslog.id.ThingId

/**
 * User-selected export scope and filters.
 */
data class ExportRequest(
  val thingIds: List<ThingId>,
  val dateRange: ExportDateRange,
  val includeOpenSquawks: Boolean,
  /** Report documents to write inside the ZIP. Attachments and README are always included. */
  val formats: Set<ExportFormat> = ExportFormat.ALL,
  /** Snapshot of the resolved automatic-delivery destination, if any. */
  val destinationEmail: String? = null,
  val destinationEmailSource: String? = null,
)
