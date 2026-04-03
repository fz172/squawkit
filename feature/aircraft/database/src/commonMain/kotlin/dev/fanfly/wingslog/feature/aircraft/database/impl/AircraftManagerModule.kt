package dev.fanfly.wingslog.feature.aircraft.database.impl

import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import org.koin.dsl.module

val aircraftDatabaseModule = module {
  single<AircraftManager> { AircraftManagerImpl(get(), get()) }
  single<MaintenanceLogManager> { MaintenanceLogManagerImpl(get(), get()) }
}
