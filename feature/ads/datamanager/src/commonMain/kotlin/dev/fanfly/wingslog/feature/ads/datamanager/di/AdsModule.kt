package dev.fanfly.wingslog.feature.ads.datamanager.di

import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.feature.ads.datamanager.AdSessionCounter
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings for free-tier display ads, registered in `core/di/CommonAppModules.kt` so every host
 * builds the same graph (a module added to one host but not the other surfaces as a runtime
 * `NoDefinitionFoundException`, not a compile error).
 *
 * Still to arrive:
 *
 * - `AdsGate` (P4) — reads `SubscriptionManager.showsAds()`, which is default-**closed**.
 * - `AdConsentManager` (P7) — `expect`/`actual`, no-op on web.
 */
val adsModule: Module = module {
  // `single`, not `factory`: the 5-unit cap is one budget shared by all three surfaces. A second
  // instance would be a second budget, silently multiplying a pilot's exposure. It resolves
  // AppForegroundObserver from `lifecycleModule`, registered ahead of this one.
  single { AdSessionCounter(foreground = get<AppForegroundObserver>()) }
}
