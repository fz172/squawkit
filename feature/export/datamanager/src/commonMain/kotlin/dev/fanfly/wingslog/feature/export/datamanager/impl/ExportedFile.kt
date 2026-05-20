package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation

/**
 * Metadata for an archive after it has been persisted to a platform-visible location.
 */
data class ExportedFile(
  /** Shareable reference to the archive: a `content://` URI on Android, a file path on iOS. */
  val filePath: String,
  /** Display name of the archive (e.g. `Hopply_Logs_N532SL_20260519.zip`). */
  val fileName: String,
  val displayLocationKind: ExportDisplayLocation,
  val sizeBytes: Long,
)
