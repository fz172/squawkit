package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.dashboard.generated.resources.Res
import wingslog.feature.aircraft.dashboard.generated.resources.tab_logs
import wingslog.feature.aircraft.dashboard.generated.resources.tab_overview
import wingslog.feature.aircraft.dashboard.generated.resources.tab_tasks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftDashboardTabRow(
  selectedTabIndex: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  PrimaryTabRow(selectedTabIndex = selectedTabIndex, modifier = modifier) {
    Tab(
      selected = selectedTabIndex == 0,
      onClick = { onTabSelected(0) },
      text = { Text(stringResource(Res.string.tab_overview)) }
    )
    Tab(
      selected = selectedTabIndex == 1,
      onClick = { onTabSelected(1) },
      text = { Text(stringResource(Res.string.tab_tasks)) }
    )
    Tab(
      selected = selectedTabIndex == 2,
      onClick = { onTabSelected(2) },
      text = { Text(stringResource(Res.string.tab_logs)) }
    )
  }
}
