package dev.fanfly.wingslog.feature.logs.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.update.logs.viewmodel.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import org.koin.dsl.module

val maintenanceUpdateModule = module {
  viewModel {
    MaintenanceLogFormViewModel(
      get<MaintenanceLogManager>(),
      get<FleetManager>(),
      get<TaskDataManager>(),
      get<SquawkManager>(),
      get<AttachmentManager>(),
      get<TechnicianManager>(),
      get<SharingManager>(),
      get<FirebaseAuth>(),
      get<DeveloperOptionsManager>(),
      get<SavedStateHandle>(),
    )
  }
}
