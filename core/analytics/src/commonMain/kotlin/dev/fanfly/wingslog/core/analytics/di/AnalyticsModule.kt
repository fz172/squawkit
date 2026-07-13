package dev.fanfly.wingslog.core.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides `single<AnalyticsManager>` bound to the platform's analytics backend. */
expect val platformAnalyticsModule: Module

/** Provides `single<AnalyticsPreferenceStore>` backed by device-local storage. */
expect val analyticsPreferenceStoreModule: Module

/**
 * Eager singleton (`createdAtStart = true`) so the persisted Firebase Logging preference is
 * applied to [dev.fanfly.wingslog.core.analytics.AnalyticsManager] at app launch, not only once
 * Settings is opened.
 */
val analyticsPreferenceModule = module {
  single(createdAtStart = true) {
    AnalyticsPreferenceController(
      get<AnalyticsPreferenceStore>(),
      get<AnalyticsManager>()
    )
  }
}
