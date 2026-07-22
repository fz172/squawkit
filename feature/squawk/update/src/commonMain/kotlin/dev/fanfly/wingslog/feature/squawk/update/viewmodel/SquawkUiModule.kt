package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val squawkUiModule = module {
  viewModel {
    SquawkFormViewModel(
      get<SquawkManager>(),
      get<AttachmentManager>(),
      get<MaintenanceLogManager>(),
      get<FirebaseAuth>(),
      get<DeveloperOptionsManager>(),
      get<SharingManager>(),
      get<SavedStateHandle>(),
    )
  }
}
