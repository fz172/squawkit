package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
actual fun getAppVersion(): String {
    return NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "Unknown"
}
