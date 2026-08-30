package dev.fanfly.wingslog.feature.fleet.datamanager.di

import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.fleet.datamanager.impl.FleetManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.dsl.module

val fleetDataManagerModule = module {
  single<FleetManager> {
    FleetManagerImpl(
      firebaseAuth = get<FirebaseAuth>(),
      templateRegistry = get<TemplateRegistry>(),
      storeFactory = get<EntityStoreFactory>(),
    )
  }
}
