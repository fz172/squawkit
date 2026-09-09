package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.common.compose.SwipeAction
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The actions a log card reveals: Delete, and only Delete (PRD §5.2). A log has no state to
 * resolve. A null [onDelete] — a read-only caller — yields an empty list, which disables the drag.
 */
@Composable
fun logQuickActions(onDelete: (() -> Unit)?): List<SwipeAction> {
  val deleteLabel = stringResource(CoreRes.string.delete)
  return if (onDelete == null) {
    emptyList()
  } else {
    listOf(
      SwipeAction(
        icon = Icons.Default.Delete,
        label = deleteLabel,
        tone = SwipeActionTone.DESTRUCTIVE,
        onClick = onDelete,
      )
    )
  }
}
