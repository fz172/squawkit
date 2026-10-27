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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

import dev.fanfly.wingslog.core.ui.adaptive.shell.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.dashboard.host.DashboardSkeleton
import dev.fanfly.wingslog.feature.logs.dashboard.LogOverviewViewModel
import dev.fanfly.wingslog.feature.squawk.dashboard.SquawkOverviewViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.NeedsAttentionSection
import dev.fanfly.wingslog.feature.tasks.dashboard.TaskOverviewViewModel
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.id.ThingId

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
  val logOverviewViewModel: LogOverviewViewModel =
    koinViewModel(
      key = "log-overview:${state.thing.id}",
      parameters = { parametersOf(state.thing) },
    )
  val logOverview by logOverviewViewModel.uiState.collectAsStateWithLifecycle()
  val squawkOverviewViewModel: SquawkOverviewViewModel =
    koinViewModel(
      key = "squawk-overview:${state.thing.id}",
      parameters = { parametersOf(state.thing.id) },
    )
  val squawkOverview by squawkOverviewViewModel.uiState.collectAsStateWithLifecycle()
  val taskOverviewViewModel: TaskOverviewViewModel =
    koinViewModel(
      key = "task-overview:${state.thing.id}",
      parameters = { parametersOf(ThingId(state.thing.id)) },
    )
  val taskOverview by taskOverviewViewModel.uiState.collectAsStateWithLifecycle()

  val snackbarHostState = LocalSnackbarHostState.current
  var pendingMessage by remember(state.thing.id) { mutableStateOf<UiText?>(null) }
  LaunchedEffect(logOverviewViewModel) {
    logOverviewViewModel.events.collect { pendingMessage = it }
  }
  val messageText = pendingMessage?.asString()
  LaunchedEffect(messageText) {
    val text = messageText ?: return@LaunchedEffect
    pendingMessage = null
    snackbarHostState?.showSnackbar(text)
  }

  if (!logOverview.loaded || !squawkOverview.loaded || !taskOverview.loaded) {
    DashboardSkeleton()
    return
  }

  val onSetReading: ((String, Double) -> Unit)? =
    if (onMutationAction != null) logOverviewViewModel::setMeterReading else null

  val tier = LocalLayoutTier.current
  if (tier == LayoutTier.LARGE) {
    LargeOverviewTab(
      state = state,
      logOverview = logOverview,
      squawkOverview = squawkOverview,
      taskOverview = taskOverview,
      onSetReading = onSetReading,
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
    OverviewHero(
      state,
      Modifier.padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.medium)
    )

    // Above the card, so what is due is the first thing read and the card has no reason to fold.
    NeedsAttentionSection(
      downSquawks = squawkOverview.aogSquawks,
      tasks = taskOverview.overdueTasks,
      onSquawkClick = { onViewSquawksTab() },
      onTaskClick = onTaskClick,
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    )

    Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
      ThingDataCard(
        state.thing,
        stats = logOverview.logStats,
        // Open to every member, technicians included: whoever can write a log can already move a
        // reading, so setting one directly is no new power (#1368).
        onSetReading = onSetReading,
        // Edit + Manage Access are owner-only; technicians get a read-only thing card (§6.3).
        onEditClick = manageAction(state, onMutationAction) {
          ThingOverviewAction.EditClick(ThingId(state.thing.id))
        },
        onManageAccessClick = memberAction(state, onMutationAction) {
          ThingOverviewAction.ManageAccessClick(ThingId(state.thing.id))
        },
      )
    }

    WorkLogsSection(
      state = state,
      logOverview = logOverview,
      onViewLogsTab = onViewLogsTab,
      onMutationAction = onMutationAction,
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
    )

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}
