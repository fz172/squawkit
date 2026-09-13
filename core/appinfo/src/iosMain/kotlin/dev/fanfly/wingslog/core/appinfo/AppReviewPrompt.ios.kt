package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/** The listing opened straight onto its review composer. */
internal const val APP_STORE_WRITE_REVIEW = "$APP_STORE_LISTING?action=write-review"

@Composable
actual fun rememberAppReviewPrompt(): AppReviewPrompt? {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    AppReviewPrompt {
      if (!IosAppReviewBridge.requestReview()) uriHandler.openUri(APP_STORE_WRITE_REVIEW)
    }
  }
}
