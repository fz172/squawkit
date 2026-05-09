package dev.fanfly.wingslog.feature.logs.viewing.di

import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceViewingModule = module {
  viewModel { params -> MaintenanceLogListViewModel(get(), get(), get(), params.get()) }
}
