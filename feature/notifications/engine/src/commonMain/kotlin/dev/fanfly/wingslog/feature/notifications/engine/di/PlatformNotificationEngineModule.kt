package dev.fanfly.wingslog.feature.notifications.engine.di

import org.koin.core.module.Module

/**
 * The platform's [dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanScheduler] binding,
 * in the same `expect`/`actual` shape as `platformNotificationDisplayModule` — a missing binding is
 * a compile error rather than a runtime `NoDefinitionFoundException`.
 *
 * Android and web build theirs eagerly (`createdAtStart`) and schedule on construction, so nothing
 * in the host has to remember to call `ensureScheduled()`. iOS cannot: `BGTaskScheduler` requires
 * its launch handler to be registered before `didFinishLaunchingWithOptions:` returns, so the host
 * drives it through `MainEntry.registerUrgencyScanTask()`.
 */
expect val platformNotificationEngineModule: Module
