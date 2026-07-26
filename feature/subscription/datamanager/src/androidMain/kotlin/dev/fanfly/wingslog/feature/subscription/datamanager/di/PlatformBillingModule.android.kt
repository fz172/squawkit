package dev.fanfly.wingslog.feature.subscription.datamanager.di

import dev.fanfly.wingslog.feature.subscription.billing.di.revenueCatBillingModule
import org.koin.core.module.Module

/** Android purchases through Google Play, via RevenueCat. */
actual val platformBillingModule: Module = revenueCatBillingModule
