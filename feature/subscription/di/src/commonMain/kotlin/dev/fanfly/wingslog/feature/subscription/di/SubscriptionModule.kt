package dev.fanfly.wingslog.feature.subscription.di

import dev.fanfly.wingslog.feature.subscription.datamanager.di.platformBillingModule
import dev.fanfly.wingslog.feature.subscription.datamanager.di.subscriptionDataManagerModule
import dev.fanfly.wingslog.feature.subscription.viewing.di.subscriptionViewingModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the subscription feature (SquawkIt Pro) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val subscriptionModule: Module = module {
  includes(
    subscriptionDataManagerModule,
    // RevenueCat on Android/iOS; the no-purchase binding on web (see PlatformBillingModule).
    platformBillingModule,
    subscriptionViewingModule,
  )
}
