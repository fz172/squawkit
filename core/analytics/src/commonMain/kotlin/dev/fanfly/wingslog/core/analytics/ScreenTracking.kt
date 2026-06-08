package dev.fanfly.wingslog.core.analytics

import androidx.navigation.NavController

/**
 * Collects [NavController.currentBackStackEntryFlow] and logs a [AnalyticsManager.logScreenView]
 * for each destination, skipping any route in [suppress]. Suspends until the controller is
 * cancelled, so call it from a `LaunchedEffect`.
 *
 * This is feeder 1 (root NavController) and feeder 3 (nested settings NavController) of the
 * page-view design; shell-section views are logged separately because they are ViewModel state,
 * not routes. See `docs/analytics/analytics_design.html` §4.
 */
suspend fun NavController.trackScreenViews(
  analytics: AnalyticsManager,
  suppress: Set<String> = emptySet(),
) {
  currentBackStackEntryFlow.collect { entry ->
    entry.destination.route
      ?.takeIf { it !in suppress }
      ?.let { analytics.logScreenView(it) }
  }
}
