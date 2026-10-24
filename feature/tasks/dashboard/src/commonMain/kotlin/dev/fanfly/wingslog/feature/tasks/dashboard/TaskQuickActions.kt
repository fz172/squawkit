package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.swipe.SwipeAction
import dev.fanfly.wingslog.core.ui.swipe.SwipeRevealController
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.ResolveTaskOptionsMenu
import dev.fanfly.wingslog.feature.tasks.viewing.TaskQuickActionCallbacks
import dev.fanfly.wingslog.feature.tasks.viewing.quickActions

/** A task card's swipe actions: resolve (with its options menu) and delete, closing the card first. */
@Composable
internal fun taskQuickActionsFor(
  item: MaintenanceTaskWithStatus,
  resolvingTaskId: String?,
  revealController: SwipeRevealController,
  onResolveClick: () -> Unit,
  onDismissResolveMenu: () -> Unit,
  onCreateWorkLog: () -> Unit,
  onSkipThisCycle: () -> Unit,
  onDeleteClick: () -> Unit,
): List<SwipeAction> =
  item.quickActions(
    TaskQuickActionCallbacks(
      onResolve = onResolveClick,
      onDelete = {
        revealController.close()
        onDeleteClick()
      },
      resolveMenu = {
        ResolveTaskOptionsMenu(
          expanded = resolvingTaskId == item.card.id,
          onDismissRequest = {
            revealController.close()
            onDismissResolveMenu()
          },
          onCreateWorkLog = {
            revealController.close()
            onCreateWorkLog()
          },
          onSkipThisCycle = {
            revealController.close()
            onSkipThisCycle()
          },
        )
      },
    )
  )
