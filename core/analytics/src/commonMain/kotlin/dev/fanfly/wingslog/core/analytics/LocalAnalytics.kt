package dev.fanfly.wingslog.core.analytics

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ambient [AnalyticsManager] for the Compose tree. Provided once at the app root; defaults to
 * [NoOpAnalyticsManager] so previews and tests don't require Koin. Mirrors `LocalLayoutTier`.
 */
val LocalAnalytics: ProvidableCompositionLocal<AnalyticsManager> =
  staticCompositionLocalOf { NoOpAnalyticsManager }
