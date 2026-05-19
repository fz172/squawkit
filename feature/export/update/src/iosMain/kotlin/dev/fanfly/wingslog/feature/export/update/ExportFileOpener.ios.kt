package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberExportFileOpener(): ExportFileOpener = remember {
  IosExportFileOpener()
}

private class IosExportFileOpener : ExportFileOpener {
  override fun open(filePath: String): Boolean {
    val url = NSURL.fileURLWithPath(filePath)
    return UIApplication.sharedApplication.openURL(url)
  }
}
