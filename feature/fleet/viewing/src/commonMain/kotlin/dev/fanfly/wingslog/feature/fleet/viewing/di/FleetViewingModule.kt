package dev.fanfly.wingslog.feature.fleet.viewing.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.AdaptiveShellViewModel
import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.FleetDashboardViewModel
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val fleetViewingModule = module {
  viewModel { FleetDashboardViewModel(get(), get(), get(), get(), get(), get()) }
  viewModel {
    AdaptiveShellViewModel(
      fleetManager = get<FleetManager>(),
      technicianManager = get<TechnicianManager>(),
      authManager = get<AuthManager>(),
    )
  }
}
