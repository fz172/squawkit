package dev.fanfly.wingslog.feature.notifications.permission.di

import dev.fanfly.wingslog.feature.notifications.permission.IosNotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationPermissionModule: Module = module {
  single<NotificationPermission> { IosNotificationPermission() }
}
