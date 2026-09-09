package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.SwipeAction
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.quick_action_resolve
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** What a squawk card's revealed actions call back into. */
data class SquawkQuickActionCallbacks(
  val onResolve: () -> Unit,
  val onDelete: () -> Unit,
  /**
   * The Resolve bubble, composed inside the Resolve button so the `Popup` anchors to it
   * (design §3.3). Null on a squawk that has no Resolve action.
   */
  val resolveMenu: (@Composable () -> Unit)? = null,
)

/**
 * The actions a squawk card reveals, per PRD §5.2: open → Resolve + Delete, dismissed or
 * addressed → Delete only. A closed squawk has nothing left to resolve.
 *
 * `@Composable` only to read the labels; the branching itself is [squawkQuickActions], which the
 * tests call directly.
 */
@Composable
fun SquawkWithStatus.quickActions(
  callbacks: SquawkQuickActionCallbacks,
): List<SwipeAction> = squawkQuickActions(
  status = status,
  resolveLabel = stringResource(CoreRes.string.quick_action_resolve),
  deleteLabel = stringResource(CoreRes.string.delete),
  callbacks = callbacks,
)

/** [quickActions] with its labels already resolved, so the state table is unit-testable. */
fun squawkQuickActions(
  status: SquawkStatus,
  resolveLabel: String,
  deleteLabel: String,
  callbacks: SquawkQuickActionCallbacks,
): List<SwipeAction> = buildList {
  if (status == SquawkStatus.OPEN) {
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
