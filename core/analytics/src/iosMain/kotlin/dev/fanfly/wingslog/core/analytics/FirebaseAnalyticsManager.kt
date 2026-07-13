package dev.fanfly.wingslog.core.analytics

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.analytics.FirebaseAnalytics

/** iOS [AnalyticsManager] backed by Firebase Analytics (→ GA4) via the GitLive binding. */
class FirebaseAnalyticsManager(
  private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsManager {
  private val log = Logger.withTag("Analytics")

  override fun logScreenView(screenName: String, params: Map<String, String>) {
    // GA4 event/param names, matching what FirebaseAnalytics.Event.SCREEN_VIEW and
    // Param.SCREEN_NAME resolve to on Android; the GitLive binding exposes no constants.
    firebaseAnalytics.logEvent(
      "screen_view",
      buildMap {
        put("screen_name", screenName)
        putAll(params)
      },
    )
    log.i { "screen_view: $screenName${if (params.isEmpty()) "" else " $params"}" }
  }

  override fun logEvent(name: String, params: Map<String, String>) {
    firebaseAnalytics.logEvent(name, params)
    log.i { "$name${if (params.isEmpty()) "" else " $params"}" }
  }

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
    firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
    log.i { "analytics collection enabled: $enabled" }
  }
}
