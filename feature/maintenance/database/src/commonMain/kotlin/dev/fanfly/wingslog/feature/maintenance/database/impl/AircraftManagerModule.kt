package dev.fanfly.wingslog.feature.maintenance.database.impl

import dev.fanfly.wingslog.feature.maintenance.database.AircraftManager
import dev.fanfly.wingslog.feature.maintenance.database.MaintenanceLogManager
import org.koin.dsl.module

val maintenanceDatabaseModule = module {
  single<AircraftManager> { AircraftManagerImpl(get(), get()) }
  single<MaintenanceLogManager> { MaintenanceLogManagerImpl(get(), get()) }
}
