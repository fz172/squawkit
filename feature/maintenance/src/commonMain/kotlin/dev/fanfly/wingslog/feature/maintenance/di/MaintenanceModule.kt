package dev.fanfly.wingslog.feature.maintenance.di

import dev.fanfly.wingslog.feature.maintenance.edit.data.EditAircraftViewModel
import dev.fanfly.wingslog.feature.maintenance.maintenance.form.data.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.maintenance.maintenance.log.data.MaintenanceLogListViewModel
import dev.fanfly.wingslog.feature.maintenance.overview.data.AircraftOverviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceModule = module {
  viewModel { AircraftOverviewViewModel(get(), get(), get(), get()) }
  viewModel { EditAircraftViewModel(get(), get()) }
  viewModel { MaintenanceLogListViewModel(get(), get()) }
  viewModel { MaintenanceLogFormViewModel(get(), get(), get(), get()) }
}
