package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController

@Composable
actual fun rememberExportFileDownloader(): ExportFileDownloader = remember {
  IosExportFileDownloader()
}

private class IosExportFileDownloader : ExportFileDownloader {

  override suspend fun download(
    filePath: String,
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean {
    if (filePath.isBlank()) return false
    val rootViewController =
      UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
    // The archive already lives in the app's sandbox (ExportFileStore); presenting the export
    // picker is the standard iOS equivalent of a Chrome-style download — the user picks a
    // destination (On My iPhone, iCloud Drive, ...) and the file lands there, outside the sandbox.
    val picker = UIDocumentPickerViewController(
      forExportingURLs = listOf(NSURL.fileURLWithPath(filePath)),
    )
    rootViewController.presentViewController(
      viewControllerToPresent = picker,
      animated = true,
      completion = null,
    )
    return true
  }
}
