package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.update.starter.StarterPackViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksUiModule = module {
  viewModel<TaskViewModel> {
    TaskViewModel(
      get<TaskDataManager>(),
      get<AttachmentManager>(),
      get<CommentManager>(),
      get<FirebaseAuth>(),
      get<MaintenanceLogManager>(),
      get<SubscriptionManager>(),
      get<SharingManager>(),
      get<TaskDueManager>(),
      get<SavedStateHandle>(),
    )
  }
  viewModel<StarterPackViewModel> {
    StarterPackViewModel(
      get<FleetManager>(),
      get<TaskDataManager>(),
      get<TemplateRegistry>(),
      get<AnalyticsManager>(),
      get<SavedStateHandle>(),
    )
  }
}
