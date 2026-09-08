package dev.fanfly.wingslog.core.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceStore
import dev.fanfly.wingslog.core.analytics.JsAnalyticsPreferenceStore
import dev.fanfly.wingslog.core.analytics.WebAnalyticsManager
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformAnalyticsModule: Module = module {
  single<AnalyticsManager> { WebAnalyticsManager() }
}

internal actual val platformAnalyticsPreferenceStoreModule: Module = module {
  single<AnalyticsPreferenceStore> { JsAnalyticsPreferenceStore() }
}
