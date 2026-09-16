package dev.fanfly.wingslog.feature.datalog.update.viewmodel

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.datalog.datamanager.ChartLayoutStore
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.update.viewer.DataLogViewerViewModel
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataLogUpdateModule: Module = module {
  viewModel { params ->
    DataLogViewerViewModel(
      get<DataLogManager>(),
      get<ChartLayoutStore>(),
      get<AnalyticsManager>(),
      get<CurrentThingTemplate>(),
      ThingId(params.get<String>(0)),
      DataLogId(params.get<String>(1)),
    )
  }
}
