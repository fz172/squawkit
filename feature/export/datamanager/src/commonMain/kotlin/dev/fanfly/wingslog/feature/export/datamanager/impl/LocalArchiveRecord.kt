package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation

/**
 * Volatile archive facts discovered from local storage.
 *
 * This is intentionally separate from persisted [dev.fanfly.wingslog.export.ExportRecord] so file
 * enumeration does not construct partial manifest records with blank IDs.
 */
internal data class LocalArchiveRecord(
  val filePath: String,
  val fileName: String,
  val sizeBytes: Long,
  val createdAtEpochMillis: Long,
  val displayLocation: ExportDisplayLocation,
)
