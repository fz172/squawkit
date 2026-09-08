package dev.fanfly.wingslog.core.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides `single<AnalyticsManager>` bound to the platform's analytics backend. */
internal expect val platformAnalyticsModule: Module

/** Provides `single<AnalyticsPreferenceStore>` backed by device-local storage. */
internal expect val platformAnalyticsPreferenceStoreModule: Module

/**
 * The one analytics entry `commonAppModules` lists: both platform bindings plus the preference
 * controller. The controller is eager (`createdAtStart = true`) so the persisted Firebase Logging
 * preference is applied to [AnalyticsManager] at app launch, not only once Settings is opened.
 */
val analyticsModule: Module = module {
  includes(platformAnalyticsModule, platformAnalyticsPreferenceStoreModule)
  single(createdAtStart = true) {
    AnalyticsPreferenceController(
      get<AnalyticsPreferenceStore>(),
      get<AnalyticsManager>()
    )
  }
}
