package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val squawkUiModule = module {
  viewModel {
    SquawkFormViewModel(
      squawkManager = get<SquawkManager>(),
      currentThingTemplate = get<CurrentThingTemplate>(),
      analytics = get<AnalyticsManager>(),
      attachmentManager = get<AttachmentManager>(),
      commentManager = get<CommentManager>(),
      logManager = get<MaintenanceLogManager>(),
      auth = get<FirebaseAuth>(),
      subscriptionManager = get<SubscriptionManager>(),
      sharingManager = get<SharingManager>(),
      savedStateHandle = get<SavedStateHandle>(),
    )
  }
}
