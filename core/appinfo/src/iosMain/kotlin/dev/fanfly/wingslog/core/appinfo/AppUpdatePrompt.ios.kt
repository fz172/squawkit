package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/**
 * The App Store's numeric app id, which a listing deep link needs and a bundle id cannot substitute
 * for. Not yet allocated in this repo, so this opens the App Store rather than the listing — a weak
 * landing spot, but a guessed id would deep-link to somebody else's app, which is worse. Set this
 * to `itms-apps://apps.apple.com/app/id<APP_STORE_ID>` once the id exists.
 */
private const val APP_STORE = "itms-apps://"

@Composable
actual fun rememberAppUpdatePrompt(): AppUpdatePrompt {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    AppUpdatePrompt(isReload = false) { uriHandler.openUri(APP_STORE) }
  }
}
