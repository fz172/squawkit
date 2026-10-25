package dev.fanfly.wingslog.feature.squawk.dashboard.detail

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.squawk.viewing.DeleteSquawkConfirmDialog
import dev.fanfly.wingslog.feature.squawk.viewing.DismissSquawkDialog
import dev.fanfly.wingslog.thing.SquawkDismissReason

/** The dismiss and delete confirmations, at tab level so the swipe container cannot clip them. */
@Composable
internal fun SquawkTabDialogs(
  state: SquawkDetailUiState,
  onConfirmDismiss: (SquawkDismissReason) -> Unit,
  onCancelDismiss: () -> Unit,
  onConfirmDelete: () -> Unit,
  onCancelDelete: () -> Unit,
) {
  if (state.dismissingSquawkId != null) {
    DismissSquawkDialog(
      onConfirm = onConfirmDismiss,
      onDismiss = onCancelDismiss,
    )
  }
  if (state.deletingSquawkId != null) {
    DeleteSquawkConfirmDialog(
      onConfirm = onConfirmDelete,
      onDismiss = onCancelDelete,
    )
  }
}
