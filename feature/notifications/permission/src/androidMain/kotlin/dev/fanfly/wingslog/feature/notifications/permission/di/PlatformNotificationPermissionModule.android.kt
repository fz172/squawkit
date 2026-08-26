package dev.fanfly.wingslog.feature.notifications.permission.di

import dev.fanfly.wingslog.core.lifecycle.CurrentActivityProvider
import dev.fanfly.wingslog.feature.notifications.permission.AndroidNotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationPermissionModule: Module = module {
  single<NotificationPermission> {
    AndroidNotificationPermission(
      context = androidContext(),
      activityProvider = get<CurrentActivityProvider>(),
    )
  }
}
