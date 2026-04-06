package dev.fanfly.wingslog.feature.fleet.datamanager.di

import dev.fanfly.wingslog.feature.fleet.datamanager.FleetDashboardManager
import dev.fanfly.wingslog.feature.fleet.datamanager.impl.FleetDashboardManagerImpl
import org.koin.dsl.module

val fleetDataManagerModule = module {
  single<FleetDashboardManager> { FleetDashboardManagerImpl(get(), get()) }
}
