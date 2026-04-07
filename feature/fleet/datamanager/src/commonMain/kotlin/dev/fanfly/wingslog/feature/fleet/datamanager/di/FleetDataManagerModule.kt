package dev.fanfly.wingslog.feature.fleet.datamanager.di

import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.fleet.datamanager.impl.FleetManagerImpl
import org.koin.dsl.module

val fleetDataManagerModule = module {
  single<FleetManager> { FleetManagerImpl(get(), get()) }
}
