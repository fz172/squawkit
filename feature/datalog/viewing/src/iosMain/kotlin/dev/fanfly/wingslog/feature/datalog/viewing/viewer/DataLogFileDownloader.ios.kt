package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController

@Composable
actual fun rememberDataLogFileDownloader(): DataLogFileDownloader = remember {
  IosDataLogFileDownloader()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosDataLogFileDownloader : DataLogFileDownloader {

  override suspend fun download(
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean {
    val bytes = fetchBytes() ?: return false
    val tempDir = NSTemporaryDirectory() + "datalog_download"
    val fm = NSFileManager.defaultManager
    fm.createDirectoryAtPath(tempDir, true, null, null)
    val filePath = "$tempDir/$fileName"

    val nsData: NSData = bytes.usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    val written = nsData.writeToFile(filePath, atomically = true)
    if (!written) return false

    val rootViewController = withContext(Dispatchers.Main) {
      UIApplication.sharedApplication.keyWindow?.rootViewController
    } ?: return false

    withContext(Dispatchers.Main) {
      val picker = UIDocumentPickerViewController(
        forExportingURLs = listOf(NSURL.fileURLWithPath(filePath)),
      )
      rootViewController.presentViewController(
        viewControllerToPresent = picker,
        animated = true,
        completion = null,
      )
    }
    return true
  }
}
