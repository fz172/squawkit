package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabRow
import dev.fanfly.wingslog.core.ui.common.compose.IconLabelTabSpec
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.dashboard.generated.resources.Res
import wingslog.feature.aircraft.dashboard.generated.resources.tab_logs
import wingslog.feature.aircraft.dashboard.generated.resources.tab_overview
import wingslog.feature.aircraft.dashboard.generated.resources.tab_squawks
import wingslog.feature.aircraft.dashboard.generated.resources.tab_tasks

@Composable
fun AircraftDashboardTabRow(
  selectedTabIndex: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tabs = listOf(
    IconLabelTabSpec(Icons.Default.FlightTakeoff, stringResource(Res.string.tab_overview)),
    IconLabelTabSpec(Icons.Default.BugReport,     stringResource(Res.string.tab_squawks)),
    IconLabelTabSpec(Icons.Default.TaskAlt,        stringResource(Res.string.tab_tasks)),
    IconLabelTabSpec(Icons.Default.History,        stringResource(Res.string.tab_logs)),
  )
  IconLabelTabRow(
    tabs = tabs,
    selectedIndex = selectedTabIndex,
    onSelect = onTabSelected,
    modifier = modifier,
  )
}
