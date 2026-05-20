package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable

/**
 * Shares a completed export archive through the platform share surface.
 */
interface ExportFileSharer {
  /**
   * Returns true when the platform accepted the share request.
   */
  fun share(filePath: String, chooserTitle: String): Boolean
}

/**
 * Remembers the platform implementation used by the export success action.
 */
@Composable
expect fun rememberExportFileSharer(): ExportFileSharer
