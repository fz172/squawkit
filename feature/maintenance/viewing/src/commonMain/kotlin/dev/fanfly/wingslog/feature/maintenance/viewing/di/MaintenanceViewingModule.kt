package dev.fanfly.wingslog.feature.maintenance.viewing.di

import dev.fanfly.wingslog.feature.maintenance.viewing.log.data.MaintenanceLogListViewModel
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceViewingModule = module {
  viewModel { AircraftOverviewViewModel(get(), get(), get(), get(), get(), get()) }
  viewModel { MaintenanceLogListViewModel(get(), get(), get()) }
}
