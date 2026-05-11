package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
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
      get<FeatureLabManager>(),
      get<SavedStateHandle>(),
    )
  }
}
