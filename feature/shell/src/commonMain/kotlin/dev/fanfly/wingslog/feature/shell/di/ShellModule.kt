package dev.fanfly.wingslog.feature.shell.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.shell.viewmodel.AdaptiveShellViewModel
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val shellModule = module {
  viewModel {
    AdaptiveShellViewModel(
      fleetManager = get<FleetManager>(),
      technicianManager = get<TechnicianManager>(),
      authManager = get<AuthManager>(),
    )
  }
}
