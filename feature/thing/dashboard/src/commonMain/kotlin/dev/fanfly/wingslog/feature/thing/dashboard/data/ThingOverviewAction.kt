package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.SquawkDismissReason

sealed interface ThingOverviewAction {
  data object BackClick : ThingOverviewAction
  data class EditClick(val thingId: String) : ThingOverviewAction
  data class ManageAccessClick(val thingId: String) : ThingOverviewAction
  data object DeleteConfirm : ThingOverviewAction
  data class AddLogClick(val thingId: String) : ThingOverviewAction
  data class EditLogClick(val thingId: String, val logId: String) :
    ThingOverviewAction

  data class AddTaskClick(val thingId: String) : ThingOverviewAction

  /** The template's starter pack, re-offered from an empty Tasks tab (PRD §4.9). */
  data class AddStarterPackClick(val thingId: String) : ThingOverviewAction
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

  // ── Squawk quick actions (design §5.1) ─────────────────────────────────────

  /** Opens the Resolve bubble anchored to the card's Resolve button. */
  data class SquawkResolveClick(val squawk: SquawkWithStatus) : ThingOverviewAction
  data object DismissSquawkResolveMenu : ThingOverviewAction

  /** Navigates to Create Log with the squawk pre-linked; the section drives the navController. */
  data class SquawkFixedClick(val squawkId: String) : ThingOverviewAction
  data class SquawkDismissClick(val squawkId: String) : ThingOverviewAction
  data class ConfirmDismissSquawk(val reason: SquawkDismissReason) : ThingOverviewAction
  data object CancelDismissSquawk : ThingOverviewAction
  data class DeleteSquawkClick(val squawk: SquawkWithStatus) : ThingOverviewAction
  data object ConfirmDeleteSquawk : ThingOverviewAction
  data object CancelDeleteSquawk : ThingOverviewAction

  // ── Task quick actions (design §5.1) ───────────────────────────────────────

  data class TaskResolveClick(val card: MaintenanceTaskWithStatus) : ThingOverviewAction
  data object DismissTaskResolveMenu : ThingOverviewAction

  /** Navigates to Create Log with the task pre-linked; the section drives the navController. */
  data class TaskCreateLogClick(val cardId: String) : ThingOverviewAction
  data class TaskSkipClick(val card: MaintenanceTaskWithStatus) : ThingOverviewAction
  data object ConfirmSkipTask : ThingOverviewAction
  data object CancelSkipTask : ThingOverviewAction

  /** Sets `deletingTaskId`, whose Confirm / Cancel pair already existed for the detail sheet. */
  data class DeleteTaskClick(val card: MaintenanceTaskWithStatus) : ThingOverviewAction
}
