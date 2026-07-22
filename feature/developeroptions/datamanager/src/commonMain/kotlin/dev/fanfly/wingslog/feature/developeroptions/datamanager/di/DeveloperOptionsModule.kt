package dev.fanfly.wingslog.feature.developeroptions.datamanager.di

import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.impl.DeveloperOptionsManagerImpl
import org.koin.dsl.module
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.fanfly.wingslog.core.storage.EntityStoreFactory

val developerOptionsModule = module {
  single<DeveloperOptionsManager> {
    DeveloperOptionsManagerImpl(
      firebaseAuth = get<FirebaseAuth>(),
      storeFactory = get<EntityStoreFactory>(),
    )
  }
}
