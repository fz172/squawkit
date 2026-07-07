package dev.fanfly.wingslog.feature.aircraft.dashboard.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewViewModel
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aircraftDashboardModule = module {
  // aircraftId comes from an explicit parameter (adaptive shell, ambient selection) when present,
  // otherwise from the navigation SavedStateHandle (legacy maintenance_overview/{aircraftId} route).
  viewModel { params ->
    val aircraftId = params.getOrNull<String>()
      ?: checkNotNull(get<SavedStateHandle>().get<String>(Screen.AIRCRAFT_ID))
    AircraftOverviewViewModel(
      get<FleetManager>(),
      get<MaintenanceLogManager>(),
      get<TaskDataManager>(),
      get<TaskDueManager>(),
      get<AttachmentOpener>(),
      get<AttachmentManager>(),
      get<SquawkManager>(),
      get<FirebaseAuth>(),
      aircraftId,
    )
  }
}
