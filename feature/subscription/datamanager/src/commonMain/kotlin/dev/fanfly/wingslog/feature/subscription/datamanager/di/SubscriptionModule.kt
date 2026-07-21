package dev.fanfly.wingslog.feature.subscription.datamanager.di

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.datamanager.impl.SubscriptionManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.dsl.module

val subscriptionModule = module {
  single<SubscriptionManager> {
    SubscriptionManagerImpl(
      firebaseAuth = get<FirebaseAuth>(),
      storeFactory = get<EntityStoreFactory>(),
      appCapability = get<AppCapability>(),
      // forceStatus defaults to "no override"; Developer Options wires the real source in P3.
    )
  }
}
