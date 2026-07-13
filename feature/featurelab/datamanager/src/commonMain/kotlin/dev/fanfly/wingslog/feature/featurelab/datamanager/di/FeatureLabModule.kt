package dev.fanfly.wingslog.feature.featurelab.datamanager.di

import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.impl.FeatureLabManagerImpl
import org.koin.dsl.module
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.fanfly.wingslog.core.storage.EntityStoreFactory

val featureLabModule = module {
  single<FeatureLabManager> {
    FeatureLabManagerImpl(
      firebaseAuth = get<FirebaseAuth>(),
      storeFactory = get<EntityStoreFactory>(),
    )
  }
}
