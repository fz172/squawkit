package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation

/**
 * Metadata for an archive after it has been persisted to a platform-visible location.
 */
data class ExportedFile(
  val filePath: String,
  val displayLocationKind: ExportDisplayLocation,
  val sizeBytes: Long,
)
