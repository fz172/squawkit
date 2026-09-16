package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_new_pane
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_new_pane_hint

/** Registers this composable's window bounds as a drop target while it is on screen. */
@Composable
fun Modifier.dropTarget(
  target: DropTarget,
  dragState: SeriesDragState
): Modifier {
  DisposableEffect(target, dragState) {
    onDispose { dragState.unregister(target) }
  }
  return onGloballyPositioned {
    dragState.register(
      target,
      it.boundsInWindow()
    )
  }
}

/** The strip under the panes (PRD R21, R24): tap opens an empty pane, a dropped chip splits into one. */
@Composable
fun NewPaneTarget(
  dragState: SeriesDragState,
  onTap: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hovered = dragState.hovered() == DropTarget.NewPane
  val accent = MaterialTheme.colorScheme.tertiary
  Surface(
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = if (hovered) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      Spacing.hairline,
      if (hovered) accent else MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = modifier
      .fillMaxWidth()
      .dropTarget(DropTarget.NewPane, dragState)
      .clickable(onClick = onTap),
  ) {
    Row(
      modifier = Modifier.padding(Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(
        Spacing.small,
        Alignment.CenterHorizontally
      ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.Add,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        stringResource(Res.string.data_log_new_pane),
        style = MaterialTheme.typography.labelLarge
      )
      Text(
        stringResource(Res.string.data_log_new_pane_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
