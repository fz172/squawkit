package dev.fanfly.wingslog.feature.maintenance.update.di

import dev.fanfly.wingslog.feature.maintenance.update.aircraft.viewmodel.EditAircraftViewModel
import dev.fanfly.wingslog.feature.maintenance.update.logs.viewmodel.MaintenanceLogFormViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceUpdateModule = module {
  viewModel { EditAircraftViewModel(get(), get()) }
  viewModel { MaintenanceLogFormViewModel(get(), get(), get(), get(), get(), get()) }
}
