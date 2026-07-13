package dev.fanfly.wingslog.feature.fleet.datamanager.di

import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.fleet.datamanager.impl.FleetManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.dsl.module

val fleetDataManagerModule = module {
  single<FleetManager> {
    FleetManagerImpl(
      get<FirebaseAuth>(),
      get<EntityStoreFactory>()
    )
  }
}
