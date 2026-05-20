package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val exportUiModule = module {
  viewModel {
    ExportViewModel(
      exportManager = get<ExportManager>(),
      fleetManager = get<FleetManager>(),
      logsManager = get<MaintenanceLogManager>(),
      taskDataManager = get<TaskDataManager>(),
      squawkManager = get<SquawkManager>(),
    )
  }
  viewModel {
    ExportHistoryViewModel(
      exportManager = get<ExportManager>(),
    )
  }
}
