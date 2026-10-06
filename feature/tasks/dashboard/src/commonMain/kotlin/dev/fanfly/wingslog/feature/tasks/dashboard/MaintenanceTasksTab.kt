package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarResult
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
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.adaptive.shell.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.swipe.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import wingslog.feature.tasks.dashboard.generated.resources.Res
import wingslog.feature.tasks.dashboard.generated.resources.tasks_added
import wingslog.feature.tasks.dashboard.generated.resources.undo

@Composable
fun MaintenanceTasksTab(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  /** Opens the account upgrade, for a guest's *Browse suggested tasks* (PRD R47). */
  onLinkAccount: () -> Unit = {},
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
  val suggestViewModel: SuggestTasksEntryViewModel =
    koinViewModel(
      key = "suggest:${state.thing.id}",
      parameters = { parametersOf(state.thing.id) })
  val suggestEntry by suggestViewModel.entry.collectAsStateWithLifecycle()
  val addedViewModel: AddedTasksViewModel =
    koinViewModel(
      key = "added:${state.thing.id}",
      parameters = { parametersOf(state.thing.id) })
  val newIds by addedViewModel.newIds.collectAsStateWithLifecycle()
  val readyViewModel: ReadySuggestionsViewModel =
    koinViewModel(
      key = "ready:${state.thing.id}",
      parameters = { parametersOf(state.thing.id) })
  val readySuggestions by readyViewModel.ready.collectAsStateWithLifecycle()
  AddedSnackbar(addedViewModel)
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
    // Suggestions are asked for from the add button's sheet. An empty list also offers the
    // template's curated list (below), for a template the server keeps one for.
    // An answer that came in while the user was elsewhere: the one place AI shows here (1f).
    readySuggestions?.let { ready ->
      ReadySuggestionsCard(
        ready = ready,
        // The default mode shows the held answer rather than starting a run (R19).
        onReview = { onAction(ThingOverviewAction.AddStarterPackClick(state.thing.id)) },
      )
    }
    val isEmpty = state.activeTasks.isEmpty() && state.completedTasks.isEmpty()
    val hasCuratedList = hasCuratedList(LocalThingTemplate.current?.id)
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
      // both sub-views, and only for a template the server keeps a curated list for.
      onAddStarterPack = if (isEmpty && hasCuratedList) {
        browseSuggestedAction(
          entry = suggestEntry,
          browse = { onAction(ThingOverviewAction.AddStarterPackClick(state.thing.id)) },
          signIn = onLinkAccount,
        )
      } else null,
      scrollTargetId = scrollToTaskId,
      highlightedId = landedTaskId,
      newIds = newIds,
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
      quickActionsFor = { item ->
        taskQuickActionsFor(
          item,
          state,
          revealController,
          onAction
        )
      },
    )

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }

  // ThingSectionContent renders the skip and delete confirmations, so neither is clipped by the
  // swipe container.
}

/**
 * "6 tasks added", with *Undo*, once per batch added from suggestions (1f), on the shell's snackbar
 * host. A host that provides none (a preview) says nothing.
 */
@Composable
private fun AddedSnackbar(viewModel: AddedTasksViewModel) {
  val count by viewModel.toAnnounce.collectAsStateWithLifecycle()
  val snackbarHostState = LocalSnackbarHostState.current ?: return
  val taskNoun = LocalThingLexicon.current.taskNoun
  val message = count?.let {
    stringResource(
      Res.string.tasks_added,
      it,
      if (it == 1) taskNoun.singular else taskNoun.plural,
    )
  }
  val undo = stringResource(Res.string.undo)
  LaunchedEffect(message) {
    if (message == null) return@LaunchedEffect
    viewModel.onAnnounced()
    val result = snackbarHostState.showSnackbar(message = message, actionLabel = undo)
    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
  }
}

