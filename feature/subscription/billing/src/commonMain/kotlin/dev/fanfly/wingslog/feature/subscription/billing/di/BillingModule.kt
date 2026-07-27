package dev.fanfly.wingslog.feature.subscription.billing.di

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.feature.subscription.billing.impl.RevenueCatBillingManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import org.koin.dsl.module

/**
 * Binds the RevenueCat-backed [BillingManager] on Android and iOS. Web never sees this module —
 * `platformBillingModule` resolves to the no-purchase binding there.
 */
val revenueCatBillingModule = module {
  single<BillingManager> {
    // `isDeveloperOptionsSupported` is the repo's `isDeveloperBuild` signal (see AppCapability), and
    // it is what picks the Test Store key over the production key.
    RevenueCatBillingManager(isDeveloperBuild = get<AppCapability>().isDeveloperOptionsSupported)
  }
}
