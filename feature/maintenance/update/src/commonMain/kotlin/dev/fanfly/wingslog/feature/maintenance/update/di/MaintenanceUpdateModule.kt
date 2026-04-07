package dev.fanfly.wingslog.feature.maintenance.update.di

import dev.fanfly.wingslog.feature.maintenance.update.edit.data.EditAircraftViewModel
import dev.fanfly.wingslog.feature.maintenance.update.form.data.MaintenanceLogFormViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceUpdateModule = module {
  viewModel { EditAircraftViewModel(get(), get()) }
  viewModel { MaintenanceLogFormViewModel(get(), get(), get(), get(), get(), get()) }
}
