package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberExportFileSharer(): ExportFileSharer = remember {
  IosExportFileSharer()
}

private class IosExportFileSharer : ExportFileSharer {
  override fun share(filePath: String, chooserTitle: String): Boolean {
    val url = NSURL.fileURLWithPath(filePath)
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
      ?: return false
    val activityController = UIActivityViewController(
      activityItems = listOf(url),
      applicationActivities = null,
    )
    rootViewController.presentViewController(
      viewControllerToPresent = activityController,
      animated = true,
      completion = null,
    )
    return true
  }
}
