package dev.fanfly.wingslog.feature.dashboard.api

import dev.fanfly.wingslog.id.DataLogId

sealed interface ThingOverviewAction {
  data object BackClick : ThingOverviewAction
  data class EditClick(val thingId: String) : ThingOverviewAction
  data class ManageAccessClick(val thingId: String) : ThingOverviewAction
  data object DeleteConfirm : ThingOverviewAction
  data class AddLogClick(val thingId: String) : ThingOverviewAction
  data class EditLogClick(val thingId: String, val logId: String) :
    ThingOverviewAction

  data class AddTaskClick(val thingId: String) : ThingOverviewAction

  /** The suggestions screen: the curated list from an empty Tasks tab, or a held answer (PRD §4.9). */
  data class BrowseSuggestionsClick(val thingId: String) : ThingOverviewAction

  /** A DATA_LOG attachment row was tapped: dismiss the sheet and open the viewer (design §9.3). */
  data class OpenDataLogClick(val thingId: String, val dataLogId: DataLogId) :
    ThingOverviewAction

  data class EditTaskClick(val thingId: String, val cardId: String) :
    ThingOverviewAction

  data class AddSquawkClick(val thingId: String) : ThingOverviewAction

  data class EditSquawkClick(val thingId: String, val squawkId: String) :
    ThingOverviewAction

  /** Navigates to Create Log with the squawk pre-linked; the section drives the navController. */
  data class SquawkFixedClick(val squawkId: String) : ThingOverviewAction

  /** Navigates to Create Log with the task pre-linked; the section drives the navController. */
  data class TaskCreateLogClick(val cardId: String) : ThingOverviewAction
}
