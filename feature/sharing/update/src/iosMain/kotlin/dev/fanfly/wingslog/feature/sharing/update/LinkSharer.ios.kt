package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberLinkSharer(): LinkSharer = remember { IosLinkSharer() }

private class IosLinkSharer : LinkSharer {
  override fun shareLink(url: String, chooserTitle: String): Boolean {
    val rootViewController =
      UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
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
