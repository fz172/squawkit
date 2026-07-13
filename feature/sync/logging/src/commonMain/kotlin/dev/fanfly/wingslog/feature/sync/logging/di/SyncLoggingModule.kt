package dev.fanfly.wingslog.feature.sync.logging.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.feature.sync.logging.AnalyticsSyncTelemetry
import dev.fanfly.wingslog.feature.sync.logging.SyncTelemetry
import org.koin.dsl.module

/**
 * Binds the sync safety-valve counters to analytics. Kept out of `feature:sync:data` so the sync
 * engine depends on the [SyncTelemetry] interface only, and never on the analytics backend.
 */
val syncLoggingModule = module {
  single<SyncTelemetry> { AnalyticsSyncTelemetry(get<AnalyticsManager>()) }
}
