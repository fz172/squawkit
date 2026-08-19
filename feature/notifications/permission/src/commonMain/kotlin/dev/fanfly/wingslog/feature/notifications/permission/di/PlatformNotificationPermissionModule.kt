package dev.fanfly.wingslog.feature.notifications.permission.di

import org.koin.core.module.Module

/**
 * The platform's [dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission]
 * binding, in the shape `platformAdConsentModule` / `platformBillingModule` already use —
 * `expect`/`actual` rather than a host-side Koin override, so a missing binding is a compile error
 * rather than a runtime `NoDefinitionFoundException`.
 */
expect val platformNotificationPermissionModule: Module
