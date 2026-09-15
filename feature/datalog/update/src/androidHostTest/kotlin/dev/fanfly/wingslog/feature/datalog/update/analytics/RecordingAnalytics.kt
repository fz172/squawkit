package dev.fanfly.wingslog.feature.datalog.update.analytics

import dev.fanfly.wingslog.core.analytics.AnalyticsManager

/** Keeps what was logged, in order, so a test can assert on the wire shape GA4 would receive. */
class RecordingAnalytics : AnalyticsManager {

  val events = mutableListOf<Pair<String, Map<String, String>>>()

  override fun logScreenView(screenName: String, params: Map<String, String>) = Unit

  override fun logEvent(name: String, params: Map<String, String>) {
    events += name to params
  }

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit
}
