package dev.fanfly.wingslog.fleet.dashboard.manager.impl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fanfly.wingslog.fleet.dashboard.manager.FleetDashboardManager

@Module
@InstallIn(SingletonComponent::class)
interface FleetDashboardManagerModule {

  @Binds
  fun bindFleetDashboardManager(impl: FleetDashboardManagerImpl): FleetDashboardManager
}