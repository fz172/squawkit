package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

sealed interface AircraftOverviewAction {
  data object BackClick : AircraftOverviewAction
  data class EditClick(val aircraftId: String) : AircraftOverviewAction
  data object DeleteConfirm : AircraftOverviewAction
  data class AddLogClick(val aircraftId: String) : AircraftOverviewAction
  data class EditLogClick(val aircraftId: String, val logId: String) : AircraftOverviewAction
  data class AddTaskClick(val aircraftId: String) : AircraftOverviewAction
  data class TaskCardClick(val card: MaintenanceTaskWithStatus) : AircraftOverviewAction
  data object DismissTaskDetail : AircraftOverviewAction
  data class EditTaskClick(val aircraftId: String, val cardId: String) :
    AircraftOverviewAction

  data object CancelDeleteTask : AircraftOverviewAction
  data object ConfirmDeleteTask : AircraftOverviewAction
  data class TaskFromLogClick(val taskId: String) : AircraftOverviewAction
  data object DismissLegacyBanner : AircraftOverviewAction
}
