package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.SwipeAction
import dev.fanfly.wingslog.core.ui.common.compose.SwipeRevealController
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.ResolveTaskOptionsMenu
import dev.fanfly.wingslog.feature.tasks.viewing.TaskQuickActionCallbacks
import dev.fanfly.wingslog.feature.tasks.viewing.quickActions
import kotlinx.coroutines.flow.first

/** A task card's swipe actions: resolve (with its options menu) and delete, closing the card first. */
@Composable
internal fun taskQuickActionsFor(
  item: MaintenanceTaskWithStatus,
  state: ThingOverviewUiState.Success,
  revealController: SwipeRevealController,
  onAction: (ThingOverviewAction) -> Unit,
): List<SwipeAction> =
  item.quickActions(
    TaskQuickActionCallbacks(
      onResolve = { onAction(ThingOverviewAction.TaskResolveClick(item)) },
      onDelete = {
        revealController.close()
        onAction(ThingOverviewAction.DeleteTaskClick(item))
      },
      resolveMenu = {
        ResolveTaskOptionsMenu(
          expanded = state.resolvingTaskId == item.card.id,
          onDismissRequest = {
            revealController.close()
            onAction(ThingOverviewAction.DismissTaskResolveMenu)
          },
          onCreateWorkLog = {
            revealController.close()
            onAction(ThingOverviewAction.TaskCreateLogClick(item.card.id))
          },
          onSkipThisCycle = {
            revealController.close()
            onAction(ThingOverviewAction.TaskSkipClick(item))
          },
        )
      },
    )
  )
