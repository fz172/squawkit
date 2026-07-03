package dev.fanfly.wingslog.core.analytics

import co.touchlab.kermit.Logger
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

/** Android [AnalyticsManager] backed by Firebase Analytics (→ GA4). */
class FirebaseAnalyticsManager(
  private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsManager {
  private val log = Logger.withTag("Analytics")

  override fun logScreenView(screenName: String, params: Map<String, String>) {
    firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
      param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
      params.forEach { (key, value) -> param(key, value) }
    }
    log.i { "screen_view: $screenName${if (params.isEmpty()) "" else " $params"}" }
  }

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) {
    firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
    log.i { "analytics collection enabled: $enabled" }
  }
}
