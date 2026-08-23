package dev.fanfly.wingslog.feature.notifications.devoptions.di

import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import dev.fanfly.wingslog.feature.notifications.devoptions.NotificationDeveloperOptionsExtra
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanDiagnostics
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanner
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Contributes the notification section into Developer Options. Registered directly in
 * `commonAppModules` (via `core:di`) rather than through a host-level bundle like
 * `stressTestKoinModules()` — unlike the fake-data generator, this module needs no compiled-out
 * variant; `DeveloperOptionsExtra.isAvailable()` (still `true` by default, unset here — this section
 * has no capability check of its own yet) is what gates visibility, same as any other Developer
 * Options row.
 */
val notificationDevOptionsModule: Module = module {
  single {
    NotificationDeveloperOptionsExtra(
      permission = get<NotificationPermission>(),
      scanner = get<UrgencyScanner>(),
      diagnostics = get<UrgencyScanDiagnostics>(),
    )
  } bind DeveloperOptionsExtra::class
}
