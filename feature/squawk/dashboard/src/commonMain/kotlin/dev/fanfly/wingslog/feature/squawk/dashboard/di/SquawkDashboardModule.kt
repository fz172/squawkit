package dev.fanfly.wingslog.feature.squawk.dashboard.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.squawk.dashboard.SquawkOverviewViewModel
import dev.fanfly.wingslog.feature.squawk.dashboard.SquawkTabViewModel
import dev.fanfly.wingslog.feature.squawk.dashboard.detail.SquawkDetailViewModel
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.id.ThingId
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The squawk section tab and overview slice: their ViewModels, keyed per thing. */
val squawkDashboardModule = module {
  viewModel { params ->
    val raw = params.get<Any>(0)
    val thingId = if (raw is ThingId) raw else ThingId(raw as String)
    SquawkTabViewModel(
      get<SquawkManager>(),
      get<MaintenanceLogManager>(),
      get<SearchEngine>(),
      get<SearchTuning>(),
      get<AnalyticsManager>(),
      thingId,
      params.get<String>(1),
    )
  }
  viewModel { params ->
    val raw = params.get<Any>(0)
    val thingId = if (raw is ThingId) raw else ThingId(raw as String)
    SquawkDetailViewModel(
      get<SquawkManager>(),
      get<MaintenanceLogManager>(),
      get<CommentManager>(),
      get<TemplateRegistry>(),
      get<AnalyticsManager>(),
      thingId,
      params.get<String>(1),
    )
  }
  viewModel { params ->
    val raw = params.get<Any>(0)
    val thingId = if (raw is ThingId) raw else ThingId(raw as String)
    SquawkOverviewViewModel(
      get<SquawkManager>(),
      thingId,
    )
  }
}
