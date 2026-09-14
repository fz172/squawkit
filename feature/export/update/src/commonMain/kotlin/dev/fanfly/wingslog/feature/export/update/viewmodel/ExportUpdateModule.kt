package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportRunPolicy
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val exportUpdateModule = module {
  viewModel {
    ExportViewModel(
      exportManager = get<ExportManager>(),
      jobCoordinator = get<ExportJobCoordinator>(),
      fleetManager = get<FleetManager>(),
      logsManager = get<MaintenanceLogManager>(),
      taskDataManager = get<TaskDataManager>(),
      squawkManager = get<SquawkManager>(),
      subscriptionManager = get<SubscriptionManager>(),
      auth = get<FirebaseAuth>(),
      currentThingTemplate = get<CurrentThingTemplate>(),
      templateRegistry = get<TemplateRegistry>(),
      analytics = get<AnalyticsManager>(),
      runPolicy = get<ExportRunPolicy>(),
    )
  }
  viewModel {
    ExportHistoryViewModel(
      exportManager = get<ExportManager>(),
      auth = get<FirebaseAuth>(),
    )
  }
}
