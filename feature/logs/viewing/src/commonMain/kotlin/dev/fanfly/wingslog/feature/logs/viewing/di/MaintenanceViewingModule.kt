package dev.fanfly.wingslog.feature.logs.viewing.di

import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager

val maintenanceViewingModule = module {
  viewModel { params ->
    MaintenanceLogListViewModel(
      get<MaintenanceLogManager>(),
      get<TaskDataManager>(),
      params.get<String>()
    )
  }
}
