package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/**
 * The listing, by numeric app id so a slug change cannot break it.
 *
 * `https` rather than the `itms-apps://` analogue of Android’s `market://`: iOS hands
 * apps.apple.com links to the App Store app anyway, and `itms-apps://` is a silent no-op
 * wherever nothing claims that scheme — the Simulator ships no App Store app.
 */
private const val APP_STORE_LISTING = "https://apps.apple.com/app/id6801955033"

@Composable
actual fun rememberAppUpdatePrompt(): AppUpdatePrompt {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    AppUpdatePrompt(isReload = false) { uriHandler.openUri(APP_STORE_LISTING) }
  }
}
