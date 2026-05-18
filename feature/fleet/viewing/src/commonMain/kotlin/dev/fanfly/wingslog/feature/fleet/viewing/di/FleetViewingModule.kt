package dev.fanfly.wingslog.feature.fleet.viewing.di

import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.FleetDashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val fleetViewingModule = module {
  viewModel { FleetDashboardViewModel(get(), get(), get(), get(), get()) }
}
