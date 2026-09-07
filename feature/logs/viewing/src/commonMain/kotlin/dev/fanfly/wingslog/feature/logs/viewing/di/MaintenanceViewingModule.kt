package dev.fanfly.wingslog.feature.logs.viewing.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListViewModel
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val maintenanceViewingModule = module {
  viewModel { params ->
    MaintenanceLogListViewModel(
      get<MaintenanceLogManager>(),
      get<TaskDataManager>(),
      get<SharingManager>(),
      get<TechnicianManager>(),
      get<SquawkManager>(),
      get<FirebaseAuth>(),
      get<SearchEngine>(),
      get<SearchTuning>(),
      get<AnalyticsManager>(),
      params.get<String>(0),
      params.get<String>(1),
    )
  }
}
