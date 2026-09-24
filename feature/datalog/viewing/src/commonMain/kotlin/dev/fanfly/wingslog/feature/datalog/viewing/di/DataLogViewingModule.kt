package dev.fanfly.wingslog.feature.datalog.viewing.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.datalog.datamanager.ChartLayoutStore
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.MapTileProvider
import dev.fanfly.wingslog.feature.datalog.viewing.attach.DataLogAttachmentPickerViewModel
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogListViewModel
import dev.fanfly.wingslog.feature.datalog.viewing.viewer.DataLogViewerViewModel
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataLogViewingModule: Module = module {
  // The tile source the map pane draws (design §11.6); a host can swap it without touching the pane.
  single<MapTileProvider> { MapTileProvider.OpenStreetMap }
  viewModel { params ->
    DataLogListViewModel(
      get<DataLogManager>(),
      get<AuthManager>(),
      get<AnalyticsManager>(),
      get<CurrentThingTemplate>(),
      ThingId(params.get<String>(0))
    )
  }
  viewModel { params ->
    DataLogAttachmentPickerViewModel(
      get<DataLogManager>(),
      get<AuthManager>(),
      get<AnalyticsManager>(),
      get<CurrentThingTemplate>(),
      ThingId(params.get<String>(0))
    )
  }

  // The viewer: a read surface with a delete action, so it lives here rather than in an
  // update/ module of its own (there is nothing to edit in a data log).
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
