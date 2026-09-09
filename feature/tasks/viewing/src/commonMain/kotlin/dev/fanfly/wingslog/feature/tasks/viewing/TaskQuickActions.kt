package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.SwipeAction
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.quick_action_resolve
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** What a task card's revealed actions call back into. */
data class TaskQuickActionCallbacks(
  val onResolve: () -> Unit,
  val onDelete: () -> Unit,
  /**
   * The Resolve bubble, composed inside the Resolve button so the `Popup` anchors to it
   * (design §3.3). Null on a task that has no Resolve action.
   */
  val resolveMenu: (@Composable () -> Unit)? = null,
)

/**
 * The actions a task card reveals, per PRD §5.2: anything still outstanding — due, due soon,
 * overdue, or on condition — gets Resolve + Delete; a complied card, which only the History filter
 * lists, gets Delete only.
 *
 * `@Composable` only to read the labels; the branching itself is [taskQuickActions], which the
 * tests call directly.
 */
@Composable
fun MaintenanceTaskWithStatus.quickActions(
  callbacks: TaskQuickActionCallbacks,
): List<SwipeAction> = taskQuickActions(
  status = dueStatus.status,
  resolveLabel = stringResource(CoreRes.string.quick_action_resolve),
  deleteLabel = stringResource(CoreRes.string.delete),
  callbacks = callbacks,
)

/** [quickActions] with its labels already resolved, so the state table is unit-testable. */
fun taskQuickActions(
  status: DueStatus,
  resolveLabel: String,
  deleteLabel: String,
  callbacks: TaskQuickActionCallbacks,
): List<SwipeAction> = buildList {
  if (status != DueStatus.COMPLIED) {
    add(
      SwipeAction(
        icon = Icons.Default.TaskAlt,
        label = resolveLabel,
        tone = SwipeActionTone.POSITIVE,
        onClick = callbacks.onResolve,
        menuContent = callbacks.resolveMenu,
      )
    )
  }
  add(
    SwipeAction(
      icon = Icons.Default.Delete,
      label = deleteLabel,
      tone = SwipeActionTone.DESTRUCTIVE,
      onClick = callbacks.onDelete,
    )
  )
}
