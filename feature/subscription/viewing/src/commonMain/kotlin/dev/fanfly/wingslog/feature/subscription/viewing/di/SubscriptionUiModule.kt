package dev.fanfly.wingslog.feature.subscription.viewing.di

import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val subscriptionUiModule = module {
  viewModel { SubscriptionViewModel(
      subscriptionManager = get<SubscriptionManager>(),
      billingManager = get<BillingManager>(),
      entitlementReconciler = get<EntitlementReconciler>(),
    ) }
}
