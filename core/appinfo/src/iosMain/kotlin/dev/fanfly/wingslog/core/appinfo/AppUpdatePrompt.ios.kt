package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

@Composable
actual fun rememberAppUpdatePrompt(): AppUpdatePrompt {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    AppUpdatePrompt(isReload = false) { uriHandler.openUri(APP_STORE_LISTING) }
  }
}
