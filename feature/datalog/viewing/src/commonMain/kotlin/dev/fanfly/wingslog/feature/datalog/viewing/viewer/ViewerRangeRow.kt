package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.TimeTicks
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_reset

/** The window the panes are showing, with the button that puts the whole log back on screen. */
@Composable
internal fun ViewerRangeRow(view: ViewWindow, onReset: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = "${TimeTicks.label(view.startSeconds)} – ${TimeTicks.label(view.endSeconds)}",
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onReset) {
      Text(stringResource(Res.string.data_log_viewer_reset))
    }
  }
}
