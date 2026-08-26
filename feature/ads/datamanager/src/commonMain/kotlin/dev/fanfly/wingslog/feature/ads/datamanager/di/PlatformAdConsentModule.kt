package dev.fanfly.wingslog.feature.ads.datamanager.di

import org.koin.core.module.Module

/**
 * The platform's [dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager] binding, in the
 * shape `platformBillingModule` / `platformAttachmentModule` already use.
 *
 * Android binds the real Google UMP implementation. iOS binds a Kotlin-side bridge that calls into
 * Swift, mirroring `IosAppCheckBridge` — `expect`/`actual` rather than a host-side Koin override so
 * a missing binding is a compile error rather than a runtime `NoDefinitionFoundException`. Web binds
 * a no-op: it carries no ads in v1, so nothing ever calls `ensureConsent()` there, but the shared
 * `AdsModule` wiring still needs a binding to compile.
 */
expect val platformAdConsentModule: Module
