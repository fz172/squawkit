package dev.fanfly.wingslog.feature.notifications.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.feature.notifications.analytics.AnalyticsUrgencyTelemetry
import dev.fanfly.wingslog.feature.notifications.analytics.UrgencyTelemetry
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The N2 metric sink (design §6.6, §12.3). Registered ahead of `notificationEngineModule` in the
 * feature's bundle, since the scanner consumes this and not the reverse.
 *
 * Lazily resolved, never `createdAtStart`: [AnalyticsUrgencyTelemetry] holds a [FirebaseAuth], and
 * building it during startup is what NPEs on iOS.
 */
val notificationAnalyticsModule: Module = module {
  single<UrgencyTelemetry> {
    AnalyticsUrgencyTelemetry(
      analytics = get<AnalyticsManager>(),
      auth = get<FirebaseAuth>(),
      cloudSync = get<CloudSyncSetting>(),
    )
  }
}
