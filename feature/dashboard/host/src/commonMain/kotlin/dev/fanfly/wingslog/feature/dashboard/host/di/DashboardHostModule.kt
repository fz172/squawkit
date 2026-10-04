package dev.fanfly.wingslog.feature.dashboard.host.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.dashboard.host.ThingOverviewViewModel
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The dashboard host: the section ViewModel that aggregates every feature for one thing. */
val dashboardHostModule = module {
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
      get<DataLogManager>(),
      get<SquawkManager>(),
      get<CommentManager>(),
      get<SharingManager>(),
      get<ThingScopeResolver>(),
      get<TemplateRegistry>(),
      get<AnalyticsManager>(),
      get<FirebaseAuth>(),
      thingId,
    )
  }
}
