package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.bar.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_sidebar_open
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_clock_axis
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** The viewer's bar: the log's date, the clock-axis toggle, the sidebar handle on a phone, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerTopBar(
  title: String,
  ready: Boolean,
  clockAxis: Boolean,
  onBack: () -> Unit,
  onToggleClockAxis: () -> Unit,
  onOpenSidebar: () -> Unit,
  onDelete: () -> Unit,
) {
  WingsLogTopAppBar(
    title = title,
    onBackClick = onBack,
    actions = {
      if (ready) {
        IconButton(onClick = onToggleClockAxis) {
          Icon(
            Icons.Filled.Schedule,
            contentDescription = stringResource(Res.string.data_log_viewer_clock_axis),
            tint = if (clockAxis) MaterialTheme.colorScheme.primary
            else LocalContentColor.current,
          )
        }
      }
      if (ready && LocalLayoutTier.current.isCompact) {
        IconButton(onClick = onOpenSidebar) {
          Icon(
            Icons.Filled.Tune,
            contentDescription = stringResource(Res.string.data_log_sidebar_open)
          )
        }
      }
      if (ready) {
        IconButton(onClick = onDelete) {
          Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(CoreRes.string.delete)
          )
        }
      }
    },
  )
}
