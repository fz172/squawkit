package dev.fanfly.wingslog.feature.dashboard.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController

import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.id.ThingId

// Single navigation entry point: intercept the navigation actions and drive the host navController
// directly; delegate every other (state) action to the ViewModel. This keeps add/edit for tasks,
// logs, squawks, and the thing on one deterministic path and removes the cross-ViewModel event
// relay that previously dropped log navigation. Edit actions dismiss their detail overlay first so
// it doesn't float above the pushed screen.
@Composable
internal fun rememberSectionActionHandler(
  viewModel: ThingOverviewViewModel,
  navController: NavController,
  thingId: ThingId,
): (ThingOverviewAction) -> Unit =
  remember(viewModel, navController, thingId) {
    { action ->
      when (action) {
        is ThingOverviewAction.AddLogClick ->
          navController.navigate(
            Screen.AddMaintenanceLog.createRoute(
              action.thingId
            )
          )

        is ThingOverviewAction.SquawkFixedClick ->
          navController.navigate(
            Screen.AddMaintenanceLog.createRoute(
              thingId,
              squawkId = action.squawkId,
            )
          )

        is ThingOverviewAction.TaskCreateLogClick ->
          navController.navigate(
            Screen.AddMaintenanceLog.createRoute(
              thingId,
              cardId = action.cardId,
            )
          )

        is ThingOverviewAction.EditLogClick ->
          navController.navigate(
            Screen.EditMaintenanceLog.createRoute(
              action.thingId,
              action.logId
            )
          )

        is ThingOverviewAction.AddTaskClick ->
          navController.navigate(Screen.AddTasks.createRoute(action.thingId))

        is ThingOverviewAction.BrowseSuggestionsClick ->
          navController.navigate(Screen.Suggestions.createRoute(action.thingId))

        is ThingOverviewAction.EditTaskClick ->
          navController.navigate(
            Screen.EditMaintenanceTask.createRoute(
              action.thingId,
              action.cardId
            )
          )

        is ThingOverviewAction.AddSquawkClick ->
          navController.navigate(Screen.AddSquawk.createRoute(action.thingId))

        is ThingOverviewAction.EditSquawkClick ->
          navController.navigate(
            Screen.EditSquawk.createRoute(
              action.thingId,
              action.squawkId
            )
          )

        is ThingOverviewAction.OpenDataLogClick ->
          navController.navigate(
            Screen.DataLogViewer.createRoute(
              action.thingId,
              action.dataLogId
            )
          )

        is ThingOverviewAction.EditClick ->
          navController.navigate(Screen.EditThing.createRoute(action.thingId))

        is ThingOverviewAction.ManageAccessClick ->
          navController.navigate(Screen.ManageAccess.createRoute(action.thingId))

        ThingOverviewAction.BackClick -> Unit

        else -> viewModel.onAction(action)
      }
    }
  }
