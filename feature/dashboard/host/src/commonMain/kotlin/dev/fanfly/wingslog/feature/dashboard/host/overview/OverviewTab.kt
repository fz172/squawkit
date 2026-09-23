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
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.adaptive.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.tasks.dashboard.NeedsAttentionSection
import dev.fanfly.wingslog.feature.tasks.model.needsAttention

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  onViewSquawksTab: () -> Unit = {},
  onViewLogsTab: () -> Unit = {},
  onMutationAction: ((ThingOverviewAction) -> Unit)? = onAction,
  modifier: Modifier = Modifier,
) {
  val tier = LocalLayoutTier.current
  if (tier == LayoutTier.LARGE) {
    LargeOverviewTab(
      state = state,
      onAction = onAction,
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
      downSquawks = state.aogSquawks,
      tasks = overdueTasks,
      onSquawkClick = { onViewSquawksTab() },
      onTaskClick = { onAction(ThingOverviewAction.TaskCardClick(it)) },
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    )

    Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
      ThingDataCard(
        state.thing,
        stats = state.logStats,
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
