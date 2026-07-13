package dev.fanfly.wingslog.feature.stresstest.di

import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.stresstest.StressTestViewModel
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val stressTestModule = module {
  viewModel {
    StressTestViewModel(
      get<FleetManager>(),
      get<TechnicianManager>(),
      get<TaskDataManager>(),
      get<SquawkManager>(),
      get<MaintenanceLogManager>()
    )
  }
}
