package dev.fanfly.wingslog.feature.dashboard.host.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.dashboard.host.DashboardSkeleton
import dev.fanfly.wingslog.feature.squawk.dashboard.SquawkOverviewViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.NeedsAttentionSection
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.model.needsAttention
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  onTaskClick: (MaintenanceTaskWithStatus) -> Unit = {},
  onViewSquawksTab: () -> Unit = {},
  onViewLogsTab: () -> Unit = {},
  onMutationAction: ((ThingOverviewAction) -> Unit)? = onAction,
  modifier: Modifier = Modifier,
) {
  val squawkOverviewViewModel: SquawkOverviewViewModel =
    koinViewModel(
      key = "squawk-overview:${state.thing.id}",
      parameters = { parametersOf(state.thing.id) },
    )
  val squawkOverview by squawkOverviewViewModel.uiState.collectAsStateWithLifecycle()
  if (!squawkOverview.loaded) {
    DashboardSkeleton()
    return
  }

  val tier = LocalLayoutTier.current
  if (tier == LayoutTier.LARGE) {
    LargeOverviewTab(
      state = state,
      squawkOverview = squawkOverview,
      onTaskClick = onTaskClick,
      onViewSquawksTab = onViewSquawksTab,
      onViewLogsTab = onViewLogsTab,
      onMutationAction = onMutationAction,
      modifier = modifier,
    )
    return
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      // Clear the floating pill this content now scrolls beneath (0 on non-compact tiers).
      .padding(bottom = LocalNavPillClearance.current),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
  ) {
    val overdueTasks =
      state.activeTasks.filter { it.needsAttention }

    OverviewHero(
      state,
      Modifier.padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.medium)
    )

    // Above the card, so what is due is the first thing read and the card has no reason to fold.
    NeedsAttentionSection(
      downSquawks = squawkOverview.aogSquawks,
      tasks = overdueTasks,
      onSquawkClick = { onViewSquawksTab() },
      onTaskClick = onTaskClick,
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    )

    Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
      ThingDataCard(
        state.thing,
        stats = state.logStats,
        // Open to every member, technicians included: whoever can write a log can already move a
        // reading, so setting one directly is no new power (#1368).
        onSetReading = onMutationAction?.let { act ->
          { meterKey, value ->
            act(ThingOverviewAction.SetMeterReading(meterKey, value))
          }
        },
        // Edit + Manage Access are owner-only; technicians get a read-only thing card (§6.3).
        onEditClick = manageAction(state, onMutationAction) {
          ThingOverviewAction.EditClick(state.thing.id)
        },
        onManageAccessClick = memberAction(state, onMutationAction) {
          ThingOverviewAction.ManageAccessClick(state.thing.id)
        },
      )
    }

    WorkLogsSection(
      state = state,
      onViewLogsTab = onViewLogsTab,
      onMutationAction = onMutationAction,
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    )

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}
