package dev.fanfly.wingslog.core.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.WebAnalyticsManager
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAnalyticsModule: Module = module {
  single<AnalyticsManager> { WebAnalyticsManager() }
}
