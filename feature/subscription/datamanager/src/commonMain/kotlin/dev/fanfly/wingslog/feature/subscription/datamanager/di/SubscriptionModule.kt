package dev.fanfly.wingslog.feature.subscription.datamanager.di

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.datamanager.impl.BillingIdentityCoordinator
import dev.fanfly.wingslog.feature.subscription.datamanager.impl.FirebaseEntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.impl.SubscriptionManagerImpl
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.dsl.module

val subscriptionModule = module {
  single<EntitlementReconciler> { FirebaseEntitlementReconciler() }

  single<SubscriptionManager> {
    SubscriptionManagerImpl(
      firebaseAuth = get<FirebaseAuth>(),
      storeFactory = get<EntityStoreFactory>(),
      appCapability = get<AppCapability>(),
      // The developer force-override (Developer Options → Force subscription status).
      forceStatus = get<DeveloperOptionsManager>().observe().map { it.forceSubscriptionStatus },
    )
  }

  /**
   * Eager on purpose. The store identity must be aliased to the Firebase uid *before* a purchase can
   * happen, not lazily when some screen first injects it — otherwise a purchase made early in the
   * session posts a webhook RevenueCat can only attribute to an anonymous id, and the entitlement is
   * never written. See [BillingIdentityCoordinator].
   */
  single(createdAtStart = true) {
    BillingIdentityCoordinator(
      firebaseAuth = get<FirebaseAuth>(),
      billingManager = get<BillingManager>(),
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ).also { it.start() }
  }
}
