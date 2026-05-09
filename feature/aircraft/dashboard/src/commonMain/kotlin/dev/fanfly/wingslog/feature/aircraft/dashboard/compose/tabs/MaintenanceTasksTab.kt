package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.ComplianceSection
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState

@Composable
fun MaintenanceTasksTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  var showComplied by rememberSaveable { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
  ) {
    Spacer(Modifier.height(Spacing.medium))

    ComplianceSection(
      activeTasks = state.activeTasks,
      completedTasks = state.completedTasks,
      showComplied = showComplied,
      onToggleComplied = { showComplied = it },
      onAddClick = { onAction(AircraftOverviewAction.AddTaskClick(state.aircraft.id)) },
      onCardClick = { onAction(AircraftOverviewAction.TaskCardClick(it)) },
    )

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }
}
