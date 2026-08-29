package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

sealed interface AircraftOverviewAction {
  data object BackClick : AircraftOverviewAction
  data class EditClick(val thingId: String) : AircraftOverviewAction
  data class ManageAccessClick(val thingId: String) : AircraftOverviewAction
  data object DeleteConfirm : AircraftOverviewAction
  data class AddLogClick(val thingId: String) : AircraftOverviewAction
  data class EditLogClick(val thingId: String, val logId: String) :
    AircraftOverviewAction

  data class AddTaskClick(val thingId: String) : AircraftOverviewAction
  data class TaskCardClick(val card: MaintenanceTaskWithStatus) :
    AircraftOverviewAction

  data object DismissTaskDetail : AircraftOverviewAction
  data class EditTaskClick(val thingId: String, val cardId: String) :
    AircraftOverviewAction

  data object CancelDeleteTask : AircraftOverviewAction
  data object ConfirmDeleteTask : AircraftOverviewAction
  data class AddSquawkClick(val thingId: String) : AircraftOverviewAction
  data class ShowSquawkDetail(val squawk: SquawkWithStatus) :
    AircraftOverviewAction

  data object DismissSquawkDetail : AircraftOverviewAction

  data class EditSquawkClick(val thingId: String, val squawkId: String) :
    AircraftOverviewAction
}
