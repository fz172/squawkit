package dev.fanfly.wingslog.feature.logs.dashboard.di

import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.logs.dashboard.LogOverviewViewModel
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.thing.Thing
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Overview tab's log stats and recent-logs ViewModel, keyed per thing. */
val logsDashboardModule = module {
  viewModel { params ->
    LogOverviewViewModel(
      get<MaintenanceLogManager>(),
      get<TemplateRegistry>(),
      params.get<Thing>(0),
    )
  }
}
