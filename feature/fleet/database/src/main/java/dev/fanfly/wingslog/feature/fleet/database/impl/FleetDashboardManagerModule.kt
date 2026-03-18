package dev.fanfly.wingslog.feature.fleet.database.impl

import dev.fanfly.wingslog.feature.fleet.database.FleetDashboardManager
import org.koin.dsl.module

val fleetDatabaseModule = module {
  single<FleetDashboardManager> { FleetDashboardManagerImpl(get(), get()) }
}
