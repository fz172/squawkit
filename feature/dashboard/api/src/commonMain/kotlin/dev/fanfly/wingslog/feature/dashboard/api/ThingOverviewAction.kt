package dev.fanfly.wingslog.feature.dashboard.api

import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId

sealed interface ThingOverviewAction {
  data object BackClick : ThingOverviewAction
  data class EditClick(val thingId: ThingId) : ThingOverviewAction
  data class ManageAccessClick(val thingId: ThingId) : ThingOverviewAction
  data object DeleteConfirm : ThingOverviewAction
  data class AddLogClick(val thingId: ThingId) : ThingOverviewAction
  data class EditLogClick(val thingId: ThingId, val logId: MaintenanceLogId) :
    ThingOverviewAction

  data class AddTaskClick(val thingId: ThingId) : ThingOverviewAction

  /** The suggestions screen: the curated list from an empty Tasks tab, or a held answer (PRD §4.9). */
  data class BrowseSuggestionsClick(val thingId: ThingId) : ThingOverviewAction

  /** A DATA_LOG attachment row was tapped: dismiss the sheet and open the viewer (design §9.3). */
  data class OpenDataLogClick(val thingId: ThingId, val dataLogId: DataLogId) :
    ThingOverviewAction

  data class EditTaskClick(val thingId: ThingId, val cardId: MaintenanceTaskId) :
    ThingOverviewAction

  data class AddSquawkClick(val thingId: ThingId) : ThingOverviewAction

  data class EditSquawkClick(val thingId: ThingId, val squawkId: SquawkId) :
    ThingOverviewAction

  /** Navigates to Create Log with the squawk pre-linked; the section drives the navController. */
  data class SquawkFixedClick(val squawkId: SquawkId) : ThingOverviewAction

  /** Navigates to Create Log with the task pre-linked; the section drives the navController. */
  data class TaskCreateLogClick(val cardId: MaintenanceTaskId) : ThingOverviewAction
}
