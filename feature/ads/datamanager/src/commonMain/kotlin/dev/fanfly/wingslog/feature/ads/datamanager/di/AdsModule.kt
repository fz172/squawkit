package dev.fanfly.wingslog.feature.ads.datamanager.di

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.datamanager.impl.AdSessionCounter
import dev.fanfly.wingslog.feature.ads.datamanager.impl.AdsManagerImpl
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings for free-tier display ads, registered in `core/di/CommonAppModules.kt` so every host
 * builds the same graph (a module added to one host but not the other surfaces as a runtime
 * `NoDefinitionFoundException`, not a compile error).
 *
 * Still to arrive:
 *
 * - `AdConsentManager` (P7) — `expect`/`actual`, no-op on web.
 */
val adsModule: Module = module {
  // `single`, not `factory`: the 5-unit cap is one budget shared by all three surfaces. A second
  // instance would be a second budget, silently multiplying a pilot's exposure. It resolves
  // AppForegroundObserver from `lifecycleModule`, registered ahead of this one.
  //
  // AdSessionCounter is `internal`, so this binding is only resolvable from inside this module —
  // which is the intent. P4's AdsManager is the public face; nothing outside reaches the counter
  // directly, and the UI asks the manager rather than doing its own budgeting.
  single { AdSessionCounter(foreground = get<AppForegroundObserver>()) }

  single<AdsManager> {
    AdsManagerImpl(
      subscriptionManager = get<SubscriptionManager>(),
      counter = get<AdSessionCounter>(),
      appCapability = get<AppCapability>(),
      // Developer Options → Force ads. Same shape as the force-subscription override in
      // subscriptionModule, so the two dev overrides are wired identically.
      forceAds = get<DeveloperOptionsManager>().observe()
        .map { it.forceAds },
    )
  }
}
