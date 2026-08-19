package dev.fanfly.wingslog.feature.notifications.viewing.di

import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import dev.fanfly.wingslog.feature.notifications.viewing.WebLocalNotifier
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationDisplayModule: Module = module {
  single<LocalNotifier> { WebLocalNotifier() }
}
