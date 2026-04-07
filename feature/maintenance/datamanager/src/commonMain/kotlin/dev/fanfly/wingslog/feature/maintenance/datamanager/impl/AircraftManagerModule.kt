package dev.fanfly.wingslog.feature.maintenance.datamanager.impl

import dev.fanfly.wingslog.feature.maintenance.datamanager.AircraftManager
import dev.fanfly.wingslog.feature.maintenance.datamanager.MaintenanceLogManager
import org.koin.dsl.module

val maintenanceDataManagerModule = module {
  single<AircraftManager> { AircraftManagerImpl(get(), get()) }
  single<MaintenanceLogManager> { MaintenanceLogManagerImpl(get(), get()) }
}
