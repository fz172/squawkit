package dev.fanfly.wingslog.feature.aircraft.dashboard.di

import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aircraftDashboardModule = module {
  viewModel { AircraftOverviewViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
