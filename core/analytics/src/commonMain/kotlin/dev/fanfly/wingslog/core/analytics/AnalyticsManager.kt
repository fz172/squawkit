package dev.fanfly.wingslog.core.analytics

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
