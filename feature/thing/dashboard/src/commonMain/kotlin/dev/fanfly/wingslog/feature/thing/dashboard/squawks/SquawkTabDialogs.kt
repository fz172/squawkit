package dev.fanfly.wingslog.feature.thing.dashboard.squawks

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.squawk.viewing.DeleteSquawkConfirmDialog
import dev.fanfly.wingslog.feature.squawk.viewing.DismissSquawkDialog
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewAction
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewUiState

/** The dismiss and delete confirmations, at tab level so the swipe container cannot clip them. */
@Composable
internal fun SquawkTabDialogs(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
) {
  if (state.dismissingSquawkId != null) {
    DismissSquawkDialog(
      onConfirm = { onAction(ThingOverviewAction.ConfirmDismissSquawk(it)) },
      onDismiss = { onAction(ThingOverviewAction.CancelDismissSquawk) },
    )
  }
  if (state.deletingSquawkId != null) {
    DeleteSquawkConfirmDialog(
      onConfirm = { onAction(ThingOverviewAction.ConfirmDeleteSquawk) },
      onDismiss = { onAction(ThingOverviewAction.CancelDeleteSquawk) },
    )
  }
}
