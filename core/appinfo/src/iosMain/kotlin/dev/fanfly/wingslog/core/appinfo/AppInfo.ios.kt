package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
actual fun getAppVersion(): String {
  val info = NSBundle.mainBundle.infoDictionary
  val shortVersion = info?.get("CFBundleShortVersionString") as? String ?: "Unknown"
  val buildNumber = info?.get("CFBundleVersion") as? String
  return if (buildNumber != null) "$shortVersion($buildNumber)" else shortVersion
}
