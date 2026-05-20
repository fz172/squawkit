package dev.fanfly.wingslog.feature.export.datamanager

/**
 * A previously generated export archive discovered on disk.
 */
data class ExportRecord(
  /** Shareable reference to the archive: a `content://` URI on Android, a file path on iOS. */
  val filePath: String,
  /** Display name of the archive (e.g. `Hopply_Logs_N532SL_20260519.zip`). */
  val fileName: String,
  val sizeBytes: Long,
  /** Creation/last-modified time in epoch milliseconds, used to sort and label the entry. */
  val createdAtEpochMillis: Long,
  val displayLocationKind: ExportDisplayLocation = ExportDisplayLocation.UNKNOWN,
)
