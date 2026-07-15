package dev.fanfly.wingslog.feature.logs.viewing.di

import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth

val maintenanceViewingModule = module {
  viewModel { params ->
    MaintenanceLogListViewModel(
      get<MaintenanceLogManager>(),
      get<TaskDataManager>(),
      get<SharingManager>(),
      get<TechnicianManager>(),
      get<SquawkManager>(),
      get<FirebaseAuth>(),
      params.get<String>()
    )
  }
}
