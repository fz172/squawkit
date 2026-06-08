package dev.fanfly.wingslog.core.analytics

import co.touchlab.kermit.Logger

/**
 * Cross-platform product-analytics sink. Backed by Firebase Analytics (→ GA4) on each platform.
 *
 * Scope note: this first slice implements **page-view logging only** ([logScreenView]). Click and
 * timing events described in `docs/analytics/analytics_design.html` are intentionally not here yet.
 */
interface AnalyticsManager {
  /** Logs a screen/page view. [screenName] is the route or shell-section name. */
  fun logScreenView(
    screenName: String,
    params: Map<String, String> = emptyMap()
  )
}

/** No-op used as the [LocalAnalytics] default (e.g. previews) so composition never crashes. */
object NoOpAnalyticsManager : AnalyticsManager {
  override fun logScreenView(screenName: String, params: Map<String, String>) =
    Unit
}

/**
 * Logs events to Kermit instead of a real backend. Used by the iOS and web actuals until their
 * native Firebase Analytics bindings land, and useful everywhere for local verification.
 */
class LoggingAnalyticsManager : AnalyticsManager {
  private val log = Logger.withTag("Analytics")

  override fun logScreenView(screenName: String, params: Map<String, String>) {
    log.i { "screen_view: $screenName${if (params.isEmpty()) "" else " $params"}" }
  }
}
