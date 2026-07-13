package dev.fanfly.wingslog.core.analytics

/**
 * Cross-platform product-analytics sink. Backed by Firebase Analytics (→ GA4) on each platform.
 *
 * Scope note: this slice implements page-view logging ([logScreenView]) plus arbitrary named events
 * ([logEvent]). The click and timing taxonomy described in `docs/analytics/analytics_design.html` is
 * still not modelled here — [logEvent] is the escape hatch callers use until it is.
 */
interface AnalyticsManager {
  /** Logs a screen/page view. [screenName] is the route or shell-section name. */
  fun logScreenView(
    screenName: String,
    params: Map<String, String> = emptyMap()
  )

  /**
   * Logs a named event. [name] and the [params] keys must be GA4-legal (snake_case, ≤40 chars,
   * letters/digits/underscore, not starting with a digit) — they are passed through unvalidated.
   */
  fun logEvent(
    name: String,
    params: Map<String, String> = emptyMap()
  )

  /**
   * Turns Firebase Analytics collection on or off at the SDK level. When disabled, no events
   * (including screen views already queued) are sent. Backs the Settings "Firebase Logging" toggle.
   */
  fun setAnalyticsCollectionEnabled(enabled: Boolean)
}

/** No-op used as the [LocalAnalytics] default (e.g. previews) so composition never crashes. */
object NoOpAnalyticsManager : AnalyticsManager {
  override fun logScreenView(screenName: String, params: Map<String, String>) =
    Unit

  override fun logEvent(name: String, params: Map<String, String>) = Unit

  override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit
}
