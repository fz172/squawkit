package dev.fanfly.wingslog.feature.notifications.engine.di

import dev.fanfly.wingslog.feature.notifications.engine.BgTaskUrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanner
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationEngineModule: Module = module {
  // Not createdAtStart, and it does not schedule itself: BGTaskScheduler's launch handler has to be
  // registered before didFinishLaunchingWithOptions: returns, so MainEntry.registerUrgencyScanTask()
  // resolves this and drives registerBgTask() + ensureScheduled() in that order.
  single<BgTaskUrgencyScanScheduler> {
    BgTaskUrgencyScanScheduler(scanner = get<UrgencyScanner>())
  }
  single<UrgencyScanScheduler> { get<BgTaskUrgencyScanScheduler>() }
}
