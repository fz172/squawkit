package dev.fanfly.wingslog.feature.notifications.engine.di

import dev.fanfly.wingslog.feature.notifications.engine.NoOpUrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationEngineModule: Module = module {
  single<UrgencyScanScheduler> { NoOpUrgencyScanScheduler() }
}
