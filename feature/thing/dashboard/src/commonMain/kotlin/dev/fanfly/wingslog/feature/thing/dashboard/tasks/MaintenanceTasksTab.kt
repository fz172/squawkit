package dev.fanfly.wingslog.feature.thing.dashboard.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.common.compose.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewAction
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewUiState
import dev.fanfly.wingslog.feature.thing.dashboard.ThingSectionContent
import dev.fanfly.wingslog.feature.thing.dashboard.squawks.SquawkTab
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MaintenanceTasksTab(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  /** Jumped-to task (from a log's Affected Tasks): switch to its sub-view and scroll to it. */
  scrollToTaskId: String? = null,
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  var showComplied by rememberSaveable { mutableStateOf(false) }
  // One controller for the whole list, so opening a card closes whichever was open — across the
  // grid's columns on a wide tier too (PRD R5).
  val revealController = rememberSwipeRevealController()
  LaunchedEffect(showComplied) { revealController.close() }
  val analytics = LocalAnalytics.current
  val tabViewModel: TaskTabViewModel =
    koinViewModel(
      key = "tasks:${state.thing.id}",
      parameters = {
        parametersOf(
          state.thing.id,
          state.thing.template?.id.orEmpty()
        )
      },
    )
  val tabState by tabViewModel.uiState.collectAsStateWithLifecycle()
  val taskFilter by tabViewModel.filter.collectAsStateWithLifecycle()
  val setFilter = tabViewModel::onFilterChange
  val activeTasks = tabState.activeTasks
  val completedTasks = tabState.completedTasks
  val taskNoun = LocalThingLexicon.current.taskNoun

  // Jump-to-task from a log: switch to the sub-view holding the target, then scroll it into view.
  // See SquawkTab for the root-coordinate offset scheme.
  var contentTopY by remember { mutableStateOf(0f) }
  var targetCardY by remember(scrollToTaskId) { mutableStateOf<Float?>(null) }
  // Set once the scroll has landed, so the highlight plays on a card that is on screen.
  var landedTaskId by remember(scrollToTaskId) { mutableStateOf<String?>(null) }
  // null = not found in either list yet, true = in history, false = active — three states so a
  // not-yet-synced tap and a status flip both re-trigger below (see SquawkTab for why: a tapped
  // notification can arrive and be acted on before the local sync pull carrying the very status
  // change it announced has landed).
  // A jump target must be reachable whatever was filtered before.
  LaunchedEffect(scrollToTaskId) {
    if (scrollToTaskId != null && taskFilter.isActive) tabViewModel.clearFilter()
  }
  val taskInHistory: Boolean? = scrollToTaskId?.let { id ->
    when {
      state.completedTasks.any { it.card.id == id } -> true
      state.activeTasks.any { it.card.id == id } -> false
      else -> null
    }
  }
  LaunchedEffect(scrollToTaskId, taskInHistory) {
    val inHistory = taskInHistory ?: return@LaunchedEffect
    showComplied = inHistory
    // Reset on the re-run too — the target card just moved between sub-views, so its old on-screen
    // position no longer means anything.
    targetCardY = null
    val cardY = snapshotFlow { targetCardY }.filterNotNull()
      .first()
    // Both positions move with the scroll, so their difference is the card's place in the content;
    // half a viewport less puts its middle in the middle.
    scrollState.animateScrollTo(
      (cardY - contentTopY - scrollState.viewportSize / 2).roundToInt()
        .coerceAtLeast(0)
    )
    landedTaskId = scrollToTaskId
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .nestedScroll(revealController.closeOnScroll)
      .onGloballyPositioned { contentTopY = it.positionInRoot().y }
      .padding(horizontal = Spacing.screenPadding)
      // Clear the floating pill this content now scrolls beneath (0 on non-compact tiers).
      .padding(bottom = navPillAndFabClearance),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    ComplianceSection(
      activeTasks = activeTasks,
      completedTasks = completedTasks,
      showComplied = showComplied,
      onToggleComplied = {
        showComplied = it
        analytics.logScreenView("shell/tasks/${if (it) "complied" else "active"}")
      },
      onCardClick = { onAction(ThingOverviewAction.TaskCardClick(it)) },
      // "Can add it later from an empty Tasks tab" (PRD §4.9): only while the tab is empty in
      // both sub-views, and only when this Thing's own DNA still carries a pack.
      onAddStarterPack = if (
        state.thing.template?.starter_tasks.orEmpty()
          .isNotEmpty() &&
        state.activeTasks.isEmpty() && state.completedTasks.isEmpty()
      ) {
        { onAction(ThingOverviewAction.AddStarterPackClick(state.thing.id)) }
      } else null,
      scrollTargetId = scrollToTaskId,
      highlightedId = landedTaskId,
      onTargetPositioned = { targetCardY = it },
      showHeader = showHeader,
      filterBar = {
        TaskFilterBar(
          state = state,
          filter = taskFilter,
          onFilterChange = setFilter,
          showComplied = showComplied,
          activeTasks = activeTasks,
          completedTasks = completedTasks,
        )
      },
      countRow = {
        RecordCountRow(
          count = (if (showComplied) completedTasks else activeTasks).size,
          nounSingular = taskNoun.singular,
          nounPlural = taskNoun.plural,
          filterActive = taskFilter.isActive,
          onClear = { tabViewModel.clearFilter() },
          horizontalPadding = Spacing.none,
        )
      },
      matchesFor = { tabState.matches[it.card.id].orEmpty() },
      noMatch = if (taskFilter.isActive) {
        {
          NoRecordsMatch(
            nounPlural = taskNoun.plural,
            onClearFilters = { tabViewModel.clearFilter() })
        }
      } else null,
      revealController = revealController,
      quickActionsFor = { item -> taskQuickActionsFor(item, state, revealController, onAction) },
    )

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }

  // ThingSectionContent renders the skip and delete confirmations, so neither is clipped by the
  // swipe container.
}
