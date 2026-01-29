package dev.fanfly.wingslog.aircraft.manager.impl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fanfly.wingslog.aircraft.manager.AircraftManager

@Module
@InstallIn(SingletonComponent::class)
interface AircraftManagerModule {

  @Binds
  fun bindAircraftManager(impl: AircraftManagerImpl): AircraftManager

}