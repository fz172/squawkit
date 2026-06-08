package dev.fanfly.wingslog.core.analytics

import co.touchlab.kermit.Logger

/** Web [AnalyticsManager] backed by the Firebase JS Analytics SDK (→ GA4). */
class WebAnalyticsManager : AnalyticsManager {
  private val log = Logger.withTag("Analytics")

  // Lazily initialized: getAnalytics() touches window/document and must run in the browser, after
  // Firebase.initialize(...). Null if Analytics is unsupported (e.g. SSR / blocked environment).
  private val analytics: dynamic by lazy {
    try {
      getAnalytics()
    } catch (e: Throwable) {
      log.e(e) { "Firebase Analytics unavailable; events will be dropped" }
      null
    }
  }

  override fun logScreenView(screenName: String, params: Map<String, String>) {
    val instance = analytics ?: return
    val eventParams: dynamic = js("({})")
    eventParams["screen_name"] = screenName
    params.forEach { (key, value) -> eventParams[key] = value }
    try {
      logEvent(instance, "screen_view", eventParams)
    } catch (e: Throwable) {
      log.e(e) { "logEvent(screen_view) failed for $screenName" }
    }
    log.i { "screen_view: $screenName${if (params.isEmpty()) "" else " $params"}" }
  }
}
