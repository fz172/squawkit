package dev.fanfly.wingslog.feature.thing.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
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
  thingId: String,
): (ThingOverviewAction) -> Unit =
  remember(viewModel, navController, thingId) {
  { action ->
    when (action) {
      is ThingOverviewAction.AddLogClick ->
        navController.navigate(
          Screen.AddMaintenanceLog.createRoute(
            thingId
          )
        )

      // Resolve → Fixed / Create work log: the ViewModel closes the bubble and logs the
      // commit, then we open Create Log with the record pre-linked (design §5.1).
      is ThingOverviewAction.SquawkFixedClick -> {
        viewModel.onAction(action)
        navController.navigate(
          Screen.AddMaintenanceLog.createRoute(
            thingId,
            squawkId = action.squawkId,
          )
        )
      }

      is ThingOverviewAction.TaskCreateLogClick -> {
        viewModel.onAction(action)
        navController.navigate(
          Screen.AddMaintenanceLog.createRoute(
            thingId,
            cardId = action.cardId,
          )
        )
      }

      is ThingOverviewAction.EditLogClick ->
        navController.navigate(
          Screen.EditMaintenanceLog.createRoute(
            thingId,
            action.logId
          )
        )

      is ThingOverviewAction.AddTaskClick ->
        navController.navigate(
          Screen.AddMaintenanceTask.createRoute(
            thingId
          )
        )

      is ThingOverviewAction.AddStarterPackClick ->
        navController.navigate(Screen.StarterPack.createRoute(thingId))

      is ThingOverviewAction.EditTaskClick -> {
        viewModel.onAction(ThingOverviewAction.DismissTaskDetail)
        navController.navigate(
          Screen.EditMaintenanceTask.createRoute(
            thingId,
            action.cardId
          )
        )
      }

      is ThingOverviewAction.AddSquawkClick ->
        navController.navigate(Screen.AddSquawk.createRoute(thingId))

      is ThingOverviewAction.EditSquawkClick -> {
        viewModel.onAction(ThingOverviewAction.DismissSquawkDetail)
        navController.navigate(
          Screen.EditSquawk.createRoute(
            thingId,
            action.squawkId
          )
        )
      }

      is ThingOverviewAction.OpenDataLogClick ->
        navController.navigate(
          Screen.DataLogViewer.createRoute(
            ThingId(action.thingId),
            action.dataLogId
          )
        )

      is ThingOverviewAction.EditClick ->
        navController.navigate(Screen.EditThing.createRoute(thingId))

      is ThingOverviewAction.ManageAccessClick ->
        navController.navigate(Screen.ManageAccess.createRoute(thingId))

      ThingOverviewAction.BackClick -> Unit

      else -> viewModel.onAction(action)
    }
  }
  }
