package dev.fanfly.wingslog.feature.aircraft.di

import dev.fanfly.wingslog.feature.aircraft.edit.data.EditAircraftViewModel
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.data.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.data.MaintenanceLogListViewModel
import dev.fanfly.wingslog.feature.aircraft.overview.data.AircraftOverviewViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val aircraftModule = module {
  viewModel { AircraftOverviewViewModel(get(), get(), get(), get()) }
  viewModel { EditAircraftViewModel(get(), get()) }
  viewModel { MaintenanceLogListViewModel(get(), get()) }
  viewModel { MaintenanceLogFormViewModel(get(), get(), get(), get()) }
}
