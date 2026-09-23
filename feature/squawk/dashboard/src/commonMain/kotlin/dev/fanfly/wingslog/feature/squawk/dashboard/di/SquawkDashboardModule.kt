package dev.fanfly.wingslog.feature.squawk.dashboard.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.squawk.dashboard.SquawkTabViewModel
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The squawk section tab: its filter/search ViewModel, keyed per thing. */
val squawkDashboardModule = module {
  viewModel { params ->
    SquawkTabViewModel(
      get<SquawkManager>(),
      get<MaintenanceLogManager>(),
      get<SearchEngine>(),
      get<SearchTuning>(),
      get<AnalyticsManager>(),
      params.get<String>(0),
      params.get<String>(1),
    )
  }
}
