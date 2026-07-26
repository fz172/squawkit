package dev.fanfly.wingslog.feature.subscription.datamanager.di

import org.koin.core.module.Module

/**
 * The platform's [dev.fanfly.wingslog.feature.subscription.model.BillingManager] binding, in the
 * shape the repo already uses for `platformStorageModule` / `platformAttachmentModule`.
 *
 * Android and iOS bind the RevenueCat implementation; **web binds the no-purchase one**, because the
 * RevenueCat KMP SDK ships no Kotlin/JS variant and purchasing is a mobile-only capability by
 * product decision. The web app still reads and honours a subscription bought on a phone — that
 * arrives through the server-authoritative `subscriptions/{uid}` doc and the sync listener, which
 * are entirely independent of this binding.
 *
 * `expect`/`actual` rather than a host-side Koin override so the web build never even resolves the
 * RevenueCat artifact, and so a missing binding is a compile error rather than a runtime
 * `NoDefinitionFoundException`.
 */
expect val platformBillingModule: Module
