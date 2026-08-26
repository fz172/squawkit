package dev.fanfly.wingslog.feature.notifications.datamanager.di

import org.koin.core.module.Module

/**
 * The platform's [dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar]
 * binding, in the same `expect`/`actual` shape as `platformNotificationDisplayModule`.
 *
 * Platform-split rather than common because the two values the registrar stamps onto the token doc
 * — `platform` and `appVersion` — are only knowable per host, and Android's version needs a
 * `Context` that `commonMain` has no way to reach.
 *
 * **Only Android binds one today.** iOS is P5 (its token arrives through APNs, which needs the
 * certificates and entitlements of #503 first) and web is P6 (no push transport in V1 — an open tab
 * detects collaborators through the sync engine instead, design §8). Both supply an empty module,
 * so injecting `PushTokenRegistrar` there is a startup failure rather than a silent no-op that
 * would look like push "working" while registering nothing.
 */
expect val platformPushTokenModule: Module
