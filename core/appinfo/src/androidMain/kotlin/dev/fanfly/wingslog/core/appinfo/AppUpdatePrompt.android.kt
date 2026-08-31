package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/** `market://` opens the Play app directly; Play resolves the https form if it is not installed. */
private const val PLAY_LISTING = "market://details?id=dev.fanfly.wingslog"

@Composable
actual fun rememberAppUpdatePrompt(): AppUpdatePrompt {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    AppUpdatePrompt(isReload = false) { uriHandler.openUri(PLAY_LISTING) }
  }
}
