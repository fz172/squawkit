package dev.fanfly.wingslog.feature.thing.dashboard.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.thing.dashboard.data.SquawkTabViewModel
import dev.fanfly.wingslog.feature.thing.dashboard.data.TaskTabViewModel
import dev.fanfly.wingslog.feature.thing.dashboard.data.ThingOverviewViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val thingDashboardModule = module {
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
  viewModel { params ->
    TaskTabViewModel(
      get<TaskStatusManager>(),
      get<SearchEngine>(),
      get<SearchTuning>(),
      get<AnalyticsManager>(),
      params.get<String>(0),
      params.get<String>(1),
    )
  }
  // thingId comes from an explicit parameter (adaptive shell, ambient selection) when present,
  // otherwise from the navigation SavedStateHandle (legacy maintenance_overview/{thingId} route).
  viewModel { params ->
    val thingId = params.getOrNull<String>()
      ?: checkNotNull(get<SavedStateHandle>().get<String>(Screen.THING_ID))
    ThingOverviewViewModel(
      get<FleetManager>(),
      get<MaintenanceLogManager>(),
      get<TaskDataManager>(),
      get<TaskStatusManager>(),
      get<AttachmentOpener>(),
      get<AttachmentManager>(),
      get<SquawkManager>(),
      get<SharingManager>(),
      get<ThingScopeResolver>(),
      get<TemplateRegistry>(),
      get<AnalyticsManager>(),
      get<FirebaseAuth>(),
      thingId,
    )
  }
}
