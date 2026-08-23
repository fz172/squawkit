package dev.fanfly.wingslog.feature.notifications.engine.di

import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.engine.WorkManagerUrgencyScanScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationEngineModule: Module = module {
  // createdAtStart + schedule-on-construction is the whole of "called at Koin init" (design §5.4):
  // it keeps the host out of it, and enqueuing is cheap and idempotent. Safe to do eagerly —
  // WorkManager.getInstance only reads its own androidx.startup initializer, and nothing here
  // touches Firebase (which is what makes eager singletons dangerous on iOS).
  single<UrgencyScanScheduler>(createdAtStart = true) {
    WorkManagerUrgencyScanScheduler(context = androidContext())
      .also { it.ensureScheduled() }
  }
}
