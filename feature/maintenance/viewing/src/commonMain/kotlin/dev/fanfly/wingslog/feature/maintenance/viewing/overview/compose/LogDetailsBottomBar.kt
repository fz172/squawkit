package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.LogStats
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.maintenance.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.maintenance.sharedassets.generated.resources.add_log
import wingslog.feature.maintenance.viewing.generated.resources.log_details
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.maintenance.viewing.generated.resources.Res as ViewingRes

@Composable
fun LogDetailsBottomBar(
  aircraft: Aircraft?,
  logStats: LogStats?,
  modifier: Modifier = Modifier,
  onLogDetailsClick: (String) -> Unit,
  onAddLogClick: (String) -> Unit = {},
) {
  if (aircraft != null) {
    val hasLogs = (logStats?.total ?: 0L) > 0L
    BottomButtons(
      modifier = modifier,
      onPrimaryClick = { onAddLogClick(aircraft.id) },
      primaryLabel = if (hasLogs) stringResource(SharedRes.string.add_log)
      else stringResource(SharedRes.string.add_first_maintenance_log),
      onSecondaryClick = if (hasLogs) ({ onLogDetailsClick(aircraft.id) }) else null,
      secondaryLabel = stringResource(ViewingRes.string.log_details),
    )
  }
}