package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.runtime.Composable

/**
 * Saves a data log CSV file into the platform's local download location (Downloads/SquawkIt on
 * Android, the browser's Downloads on web, the Files app export picker on iOS).
 */
interface DataLogFileDownloader {
  suspend fun download(
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean
}

@Composable
expect fun rememberDataLogFileDownloader(): DataLogFileDownloader
