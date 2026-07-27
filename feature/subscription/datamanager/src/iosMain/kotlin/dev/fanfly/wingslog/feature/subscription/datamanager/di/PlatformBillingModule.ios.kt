package dev.fanfly.wingslog.feature.subscription.datamanager.di

import dev.fanfly.wingslog.feature.subscription.billing.di.revenueCatBillingModule
import org.koin.core.module.Module

/** iOS purchases through StoreKit, via RevenueCat. */
actual val platformBillingModule: Module = revenueCatBillingModule
