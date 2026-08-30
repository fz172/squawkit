package dev.fanfly.wingslog.feature.logs.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.update.logs.viewmodel.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceUpdateModule = module {
  viewModel {
    MaintenanceLogFormViewModel(
      logManager = get<MaintenanceLogManager>(),
      fleetManager = get<FleetManager>(),
      inspectionDataManager = get<TaskDataManager>(),
      squawkManager = get<SquawkManager>(),
      attachmentManager = get<AttachmentManager>(),
      technicianManager = get<TechnicianManager>(),
      sharingManager = get<SharingManager>(),
      auth = get<FirebaseAuth>(),
      subscriptionManager = get<SubscriptionManager>(),
      currentThingTemplate = get<CurrentThingTemplate>(),
      analytics = get<AnalyticsManager>(),
      savedStateHandle = get<SavedStateHandle>(),
    )
  }
}
