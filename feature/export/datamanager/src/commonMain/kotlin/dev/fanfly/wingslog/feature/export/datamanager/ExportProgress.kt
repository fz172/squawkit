package dev.fanfly.wingslog.feature.export.datamanager

/**
 * Streamed export status emitted by [ExportManager].
 */
sealed interface ExportProgress {
  data class Running(val step: ExportProgressStep, val percent: Int) : ExportProgress

  /**
   * Completed archive metadata. UI should prefer [displayLocationKind] for localized labels.
   */
  data class Success(
    val filePath: String,
    val fileName: String,
    val displayLocation: String,
    val sizeBytes: Long,
    val displayLocationKind: ExportDisplayLocation = ExportDisplayLocation.UNKNOWN,
  ) : ExportProgress

  data class Error(val message: String, val cause: Throwable? = null) : ExportProgress
}
