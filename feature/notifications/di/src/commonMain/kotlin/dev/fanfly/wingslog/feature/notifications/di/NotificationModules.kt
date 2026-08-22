package dev.fanfly.wingslog.feature.notifications.di

import dev.fanfly.wingslog.feature.notifications.datamanager.di.notificationPrefsModule
import dev.fanfly.wingslog.feature.notifications.devoptions.di.notificationDevOptionsModule
import dev.fanfly.wingslog.feature.notifications.engine.di.notificationEngineModule
import dev.fanfly.wingslog.feature.notifications.permission.di.platformNotificationPermissionModule
import dev.fanfly.wingslog.feature.notifications.settings.di.notificationSettingsModule
import dev.fanfly.wingslog.feature.notifications.viewing.di.platformNotificationDisplayModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every notification-feature-specific Koin module into the one entry `commonAppModules`
 * lists, so a feature that currently needs six modules doesn't read as six independent concerns
 * there — and so `core/di` depends on this one module instead of on all six notification
 * submodules directly. Foundational/infrastructure modules a feature happens to need (lifecycle,
 * storage, …) stay registered directly in `commonAppModules` rather than folded in here — this
 * bundles the notification feature's own business modules only.
 */
val notificationsModule: Module = module {
  includes(
    platformNotificationPermissionModule,
    platformNotificationDisplayModule,
    notificationPrefsModule,
    notificationSettingsModule,
    notificationEngineModule,
    notificationDevOptionsModule,
  )
}
