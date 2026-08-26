package dev.fanfly.wingslog.feature.notifications.viewing.di

import org.koin.core.module.Module

/**
 * The platform's [dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier] binding, in the
 * shape `platformNotificationPermissionModule` / `platformAdConsentModule` already use —
 * `expect`/`actual` rather than a host-side Koin override, so a missing binding is a compile error
 * rather than a runtime `NoDefinitionFoundException`.
 *
 * Android's actual also registers the three [dev.fanfly.wingslog.feature.notifications.model.NotificationChannel]s
 * on construction, so they exist before the first `post()` regardless of what triggers the single's
 * first resolution.
 */
expect val platformNotificationDisplayModule: Module
