package dev.fanfly.wingslog.feature.aircraft.database.impl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.database.InspectionManager
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager

@Module
@InstallIn(SingletonComponent::class)
interface AircraftManagerModule {

  @Binds
  fun bindAircraftManager(impl: AircraftManagerImpl): AircraftManager

  @Binds
  fun bindMaintenanceLogManager(impl: MaintenanceLogManagerImpl): MaintenanceLogManager

  @Binds
  fun bindInspectionManager(impl: InspectionManagerImpl): InspectionManager
}