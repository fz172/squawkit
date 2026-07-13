package dev.fanfly.wingslog.feature.stresstest.di

import dev.fanfly.wingslog.feature.stresstest.StressTestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager

val stressTestModule = module {
  viewModel { StressTestViewModel(get<FleetManager>(), get<TechnicianManager>(), get<TaskDataManager>(), get<SquawkManager>(), get<MaintenanceLogManager>()) }
}
