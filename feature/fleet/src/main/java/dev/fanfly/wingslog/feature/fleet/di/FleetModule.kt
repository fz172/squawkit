package dev.fanfly.wingslog.feature.fleet.di

import dev.fanfly.wingslog.feature.fleet.dashboard.data.FleetDashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val fleetModule = module {
  viewModel { FleetDashboardViewModel(get(), get()) }
}
