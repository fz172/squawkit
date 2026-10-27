package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.logs.dashboard.LogOnboardingCard
import dev.fanfly.wingslog.feature.logs.dashboard.LogOverviewUiState
import dev.fanfly.wingslog.feature.squawk.dashboard.SquawkOverviewUiState
import dev.fanfly.wingslog.feature.tasks.dashboard.NeedsAttentionSection
import dev.fanfly.wingslog.feature.tasks.dashboard.TaskOverviewUiState
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.id.ThingId

@Composable
internal fun LargeOverviewTab(
  state: ThingOverviewUiState.Success,
  logOverview: LogOverviewUiState,
  squawkOverview: SquawkOverviewUiState,
  taskOverview: TaskOverviewUiState,
  onSetReading: ((meterKey: String, value: Double) -> Unit)?,
  onTaskClick: (MaintenanceTaskWithStatus) -> Unit,
  onViewSquawksTab: () -> Unit,
  onViewLogsTab: () -> Unit,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.screenPadding)
      .padding(top = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    OverviewHero(state)

    NeedsAttentionSection(
      downSquawks = squawkOverview.aogSquawks,
      tasks = taskOverview.overdueTasks,
      onSquawkClick = { onViewSquawksTab() },
      onTaskClick = onTaskClick,
    )

    ThingDataCard(
      state.thing,
      stats = logOverview.logStats,
      // Open to every member, technicians included: whoever can write a log can already move a
      // reading, so setting one directly is no new power (#1368).
      onSetReading = onSetReading,
      onEditClick = manageAction(state, onMutationAction) {
        ThingOverviewAction.EditClick(ThingId(state.thing.id))
      },
      onManageAccessClick = memberAction(state, onMutationAction) {
        ThingOverviewAction.ManageAccessClick(ThingId(state.thing.id))
      },
    )

    if (logOverview.logStats?.total == 0L && onMutationAction != null) {
      LogOnboardingCard(
        onAddLogClick = { onMutationAction(ThingOverviewAction.AddLogClick(ThingId(state.thing.id))) },
      )
    }

    DashboardLowerGrid(
      logOverview = logOverview,
      nextDueTask = taskOverview.nextDueTask,
      openSquawksPreview = squawkOverview.openPreview,
      onTaskClick = onTaskClick,
      onLogsClick = onViewLogsTab,
      onViewSquawksClick = onViewSquawksTab,
    )

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}
