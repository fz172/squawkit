package dev.fanfly.wingslog.feature.notifications.permission.di

import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.WebNotificationPermission
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationPermissionModule: Module = module {
  single<NotificationPermission> { WebNotificationPermission() }
}
