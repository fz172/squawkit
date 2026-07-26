package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable

/**
 * Saves a completed export archive into the platform's normal local-file location — the same
 * place any other downloaded file would land (Downloads/SquawkIt on Android, the browser's
 * Downloads on web, the Files app on iOS).
 */
interface ExportFileDownloader {
  /**
   * [filePath]/[fileName] identify the already-local archive when the platform has a durable
   * handle (Android content URI, iOS sandbox path) and are used directly. Platforms with no such
   * handle (web) call [fetchBytes] instead, which resolves from an in-session cache or the cloud
   * copy. Returns true once the platform has accepted/completed the download.
   */
  suspend fun download(
    filePath: String,
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean
}

/**
 * Remembers the platform implementation used by the export Download action.
 */
@Composable
expect fun rememberExportFileDownloader(): ExportFileDownloader
