package dev.fanfly.wingslog.feature.notifications.viewing.di

import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.notifications.viewing.AndroidLocalNotifier
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationDisplayModule: Module = module {
  single<LocalNotifier> {
    AndroidLocalNotifier(
      context = androidContext(),
      currentThingTemplate = get<CurrentThingTemplate>(),
    )
  }
}
