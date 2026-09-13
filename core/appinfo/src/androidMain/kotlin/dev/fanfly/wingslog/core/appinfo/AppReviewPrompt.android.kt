package dev.fanfly.wingslog.core.appinfo

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import co.touchlab.kermit.Logger
import com.google.android.play.core.review.ReviewManagerFactory

private val logger = Logger.withTag("AppReviewPrompt")

/**
 * Play In-App Review. `requestReviewFlow` fails outright on a build Play did not install (a
 * sideloaded debug APK — the card only ever shows for Play-installed builds, internal app sharing
 * included), which is the one case the fallback can catch; a quota no-show completes as a success
 * with nothing on screen.
 */
@Composable
actual fun rememberAppReviewPrompt(): AppReviewPrompt? {
  val activity = LocalActivity.current
  val uriHandler = LocalUriHandler.current
  return remember(activity, uriHandler) {
    AppReviewPrompt {
      if (activity == null) {
        uriHandler.openUri(PLAY_LISTING)
        return@AppReviewPrompt
      }
      val manager = ReviewManagerFactory.create(activity)
      manager.requestReviewFlow().addOnCompleteListener { request ->
        if (request.isSuccessful) {
          manager.launchReviewFlow(activity, request.result)
        } else {
          logger.i { "In-app review unavailable, opening the listing: ${request.exception?.message}" }
          uriHandler.openUri(PLAY_LISTING)
        }
      }
    }
  }
}
