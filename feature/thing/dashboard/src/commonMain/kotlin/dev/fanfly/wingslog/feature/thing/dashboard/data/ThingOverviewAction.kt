package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

sealed interface ThingOverviewAction {
  data object BackClick : ThingOverviewAction
  data class EditClick(val thingId: String) : ThingOverviewAction
  data class ManageAccessClick(val thingId: String) : ThingOverviewAction
  data object DeleteConfirm : ThingOverviewAction
  data class AddLogClick(val thingId: String) : ThingOverviewAction
  data class EditLogClick(val thingId: String, val logId: String) :
    ThingOverviewAction

  data class AddTaskClick(val thingId: String) : ThingOverviewAction
  data class TaskCardClick(val card: MaintenanceTaskWithStatus) :
    ThingOverviewAction

  data object DismissTaskDetail : ThingOverviewAction
  data class EditTaskClick(val thingId: String, val cardId: String) :
    ThingOverviewAction

  data object CancelDeleteTask : ThingOverviewAction
  data object ConfirmDeleteTask : ThingOverviewAction
  data class AddSquawkClick(val thingId: String) : ThingOverviewAction
  data class ShowSquawkDetail(val squawk: SquawkWithStatus) :
    ThingOverviewAction

  data object DismissSquawkDetail : ThingOverviewAction

  data class EditSquawkClick(val thingId: String, val squawkId: String) :
    ThingOverviewAction
}
