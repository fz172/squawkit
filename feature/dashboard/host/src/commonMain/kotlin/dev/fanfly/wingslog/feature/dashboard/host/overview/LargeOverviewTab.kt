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
import dev.fanfly.wingslog.feature.tasks.dashboard.NeedsAttentionSection
import dev.fanfly.wingslog.feature.tasks.model.needsAttention

@Composable
internal fun LargeOverviewTab(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  onViewSquawksTab: () -> Unit,
  onViewLogsTab: () -> Unit,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val overdueTasks =
    state.activeTasks.filter { it.needsAttention }
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
      downSquawks = state.aogSquawks,
      tasks = overdueTasks,
      onSquawkClick = { onViewSquawksTab() },
      onTaskClick = { onAction(ThingOverviewAction.TaskCardClick(it)) },
    )

    ThingDataCard(
      state.thing,
      stats = state.logStats,
      onEditClick = manageAction(state, onMutationAction) {
        ThingOverviewAction.EditClick(state.thing.id)
      },
      onManageAccessClick = memberAction(state, onMutationAction) {
        ThingOverviewAction.ManageAccessClick(state.thing.id)
      },
    )

    if (state.logStats?.total == 0L && onMutationAction != null) {
      LogOnboardingCard(
        onAddLogClick = { onMutationAction(ThingOverviewAction.AddLogClick(state.thing.id)) },
      )
    }

    DashboardLowerGrid(
      state = state,
      onTaskClick = { onAction(ThingOverviewAction.TaskCardClick(it)) },
      onLogsClick = onViewLogsTab,
      onViewSquawksClick = onViewSquawksTab,
    )

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}
