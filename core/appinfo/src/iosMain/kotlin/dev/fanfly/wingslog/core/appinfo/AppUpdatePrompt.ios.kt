package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/** `itms-apps://` opens the App Store app directly on the listing; `6801955033` is SquawkIt’s app id. */
private const val APP_STORE_LISTING = "itms-apps://apps.apple.com/app/id6801955033"

@Composable
actual fun rememberAppUpdatePrompt(): AppUpdatePrompt {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    AppUpdatePrompt(isReload = false) { uriHandler.openUri(APP_STORE_LISTING) }
  }
}
