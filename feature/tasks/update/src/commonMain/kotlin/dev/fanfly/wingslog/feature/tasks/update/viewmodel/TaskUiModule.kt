package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksUiModule = module {
  viewModel<TaskViewModel> {
    TaskViewModel(
      get<TaskDataManager>(),
      get<AttachmentManager>(),
      get<FirebaseAuth>(),
      get<MaintenanceLogManager>(),
      get<FeatureLabManager>(),
      get<TaskDueManager>(),
      get<SavedStateHandle>(),
    )
  }
}
