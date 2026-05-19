package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable

/**
 * Opens a completed export archive with the platform's default document handler.
 */
interface ExportFileOpener {
  /**
   * Returns true when the platform accepted the open request.
   */
  fun open(filePath: String): Boolean
}

/**
 * Remembers the platform implementation used by the export success action.
 */
@Composable
expect fun rememberExportFileOpener(): ExportFileOpener
