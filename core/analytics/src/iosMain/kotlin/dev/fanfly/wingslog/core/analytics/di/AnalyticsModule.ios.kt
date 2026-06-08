package dev.fanfly.wingslog.core.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.LoggingAnalyticsManager
import org.koin.core.module.Module
import org.koin.dsl.module

// TODO: bind to FIRAnalytics (FirebaseAnalytics framework is linked in the Xcode project) via a
//  cinterop/Swift bridge. For now events are logged via Kermit so page views are verifiable.
actual val platformAnalyticsModule: Module = module {
  single<AnalyticsManager> { LoggingAnalyticsManager() }
}
