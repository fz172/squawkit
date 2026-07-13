package dev.fanfly.wingslog.feature.logs.datamanager.impl

import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import org.koin.dsl.module

val maintenanceDataManagerModule = module {
  single<MaintenanceLogManager> {
    MaintenanceLogManagerImpl(
      get<AircraftScopeResolver>(),
      get<EntityStoreFactory>()
    )
  }
}
