package dev.fanfly.wingslog.aircraft.manager.impl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fanfly.wingslog.aircraft.manager.AircraftManager
import dev.fanfly.wingslog.aircraft.manager.MaintenanceLogManager

@Module
@InstallIn(SingletonComponent::class)
interface AircraftManagerModule {

  @Binds
  fun bindAircraftManager(impl: AircraftManagerImpl): AircraftManager

  @Binds
  fun bindMaintenanceLogManager(impl: MaintenanceLogManagerImpl): MaintenanceLogManager
}