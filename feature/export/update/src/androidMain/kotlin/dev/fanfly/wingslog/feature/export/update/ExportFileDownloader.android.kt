package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberExportFileDownloader(): ExportFileDownloader =
  remember { AndroidExportFileDownloader() }

private class AndroidExportFileDownloader : ExportFileDownloader {

  override suspend fun download(
    filePath: String,
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean {
    // ExportFileStore already writes straight into Downloads/SquawkIt via MediaStore when the
    // export finishes, so there's nothing left to do — the archive is already where the user
    // expects a downloaded file to be.
    return filePath.isNotBlank()
  }
}
