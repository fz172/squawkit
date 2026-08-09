package dev.fanfly.wingslog.feature.ads.datamanager.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings for free-tier display ads, registered in `core/di/CommonAppModules.kt` so every host
 * builds the same graph (a module added to one host but not the other surfaces as a runtime
 * `NoDefinitionFoundException`, not a compile error).
 *
 * Empty in P1, deliberately: the module skeleton and the `isAdsSupported` capability land first so
 * the wiring is in place and provably harmless before anything can render. What arrives here later:
 *
 * - `AdSessionCounter` (P3) — **must** be a `single`. The 5-unit cap is global across all three
 *   surfaces, so a second instance would silently multiply a pilot's ad exposure by the number of
 *   instances. It also depends on `AppForegroundObserver` from `core/lifecycle`, whose module is
 *   registered ahead of this one.
 * - `AdsGate` (P4) — reads `SubscriptionManager.showsAds()`, which is default-**closed**.
 * - `AdConsentManager` (P7) — `expect`/`actual`, no-op on web.
 */
val adsModule: Module = module {
}
