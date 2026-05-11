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
import wingslog.feature.aircraft.dashboard.generated.resources.tab_squawks
import wingslog.feature.aircraft.dashboard.generated.resources.tab_tasks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftDashboardTabRow(
  selectedTabIndex: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  PrimaryTabRow(selectedTabIndex = selectedTabIndex, modifier = modifier) {
    AircraftTab.entries.forEachIndexed { index, tab ->
      val label = when (tab) {
        AircraftTab.OVERVIEW -> stringResource(Res.string.tab_overview)
        AircraftTab.SQUAWKS  -> stringResource(Res.string.tab_squawks)
        AircraftTab.TASKS    -> stringResource(Res.string.tab_tasks)
        AircraftTab.LOGS     -> stringResource(Res.string.tab_logs)
      }
      Tab(
        selected = selectedTabIndex == index,
        onClick = { onTabSelected(index) },
        text = { Text(label) },
      )
    }
  }
}
