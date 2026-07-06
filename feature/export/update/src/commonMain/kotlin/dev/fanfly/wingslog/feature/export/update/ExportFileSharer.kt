package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable

/**
 * Opens a platform email draft for a completed export archive.
 */
interface ExportFileSharer {
  /**
   * Returns true when the platform accepted the email draft request.
   */
  fun share(
    filePath: String,
    chooserTitle: String,
    subject: String,
    body: String
  ): Boolean
}

/**
 * Remembers the platform implementation used by the export success action.
 */
@Composable
expect fun rememberExportFileSharer(): ExportFileSharer
