package dev.fanfly.wingslog.feature.thing.dashboard.compose.tabs

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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterBar
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterSheet
import dev.fanfly.wingslog.feature.thing.dashboard.compose.ComplianceSection
import dev.fanfly.wingslog.feature.thing.dashboard.data.TaskTabViewModel
import dev.fanfly.wingslog.feature.thing.dashboard.data.ThingOverviewAction
import dev.fanfly.wingslog.feature.thing.dashboard.data.ThingOverviewUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes
import wingslog.feature.search.sharedassets.generated.resources.filter_records
import wingslog.feature.search.sharedassets.generated.resources.meter_task_note
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder

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
  val analytics = LocalAnalytics.current
  val useFilterBar = koinInject<AppCapability>().isSearchFilterSupported
  var showFilterSheet by remember { mutableStateOf(false) }
  val tabViewModel: TaskTabViewModel =
    koinViewModel(key = "tasks:${state.thing.id}", parameters = { parametersOf(state.thing.id) })
  val tabState by tabViewModel.uiState.collectAsStateWithLifecycle()
  val taskFilter = tabState.filter
  val setFilter = tabViewModel::onFilterChange
  val activeTasks = tabState.activeTasks
  val completedTasks = tabState.completedTasks
  val taskNoun = LocalThingLexicon.current.taskNoun

  // Jump-to-task from a log: switch to the sub-view holding the target, then scroll it into view.
  // See SquawkTab for the root-coordinate offset scheme.
  var contentTopY by remember { mutableStateOf(0f) }
  var targetCardY by remember(scrollToTaskId) { mutableStateOf<Float?>(null) }
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
    scrollState.animateScrollTo(
      (scrollState.value + (cardY - contentTopY)).roundToInt()
        .coerceAtLeast(0)
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .onGloballyPositioned { contentTopY = it.positionInRoot().y }
      .padding(horizontal = Spacing.screenPadding)
      // Clear the floating pill this content now scrolls beneath (0 on non-compact tiers).
      .padding(bottom = LocalNavPillClearance.current),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    // The bar carries its own top padding, matching the Logs tab; the spacer would double it.
    if (!useFilterBar) Spacer(Modifier.height(Spacing.medium))

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
        state.thing.template?.starter_tasks.orEmpty().isNotEmpty() &&
        state.activeTasks.isEmpty() && state.completedTasks.isEmpty()
      ) {
        { onAction(ThingOverviewAction.AddStarterPackClick(state.thing.id)) }
      } else null,
      scrollTargetId = scrollToTaskId,
      onTargetPositioned = { targetCardY = it },
      showHeader = showHeader,
      filterBar = if (useFilterBar) {
        {
          RecordFilterBar(
            filter = taskFilter,
            placeholder = stringResource(SearchRes.string.search_placeholder),
            showComponentFilter = componentTypesApply,
            componentLabel = { it.displayName() },
            onQueryChange = { setFilter(taskFilter.copy(query = it)) },
            onOpenFilters = { showFilterSheet = true },
            onRemoveComponent = { setFilter(taskFilter.toggleComponent(it)) },
            onClearTime = { setFilter(taskFilter.copy(time = TimeWindow.All)) },
            dueWithin = !showComplied,
            horizontalPadding = Spacing.none,
          )
        }
      } else null,
      countRow = if (useFilterBar) {
        {
          RecordCountRow(
            count = (if (showComplied) completedTasks else activeTasks).size,
            nounSingular = taskNoun.singular,
            nounPlural = taskNoun.plural,
            filterActive = taskFilter.isActive,
            onClear = { tabViewModel.clearFilter() },
            horizontalPadding = Spacing.none,
          )
        }
      } else null,
      noMatch = if (useFilterBar && taskFilter.isActive) {
        { NoRecordsMatch(nounPlural = taskNoun.plural, onClearFilters = { tabViewModel.clearFilter() }) }
      } else null,
    )

    if (showFilterSheet) {
      RecordFilterSheet(
        title = stringResource(SearchRes.string.filter_records, taskNoun.plural),
        filter = taskFilter,
        showComponentFilter = componentTypesApply,
        componentLabel = { it.displayName() },
        onComponentToggle = { setFilter(taskFilter.toggleComponent(it)) },
        onTimeWindowChange = { setFilter(taskFilter.copy(time = it)) },
        onClear = { setFilter(taskFilter.withoutFilters()) },
        onDismiss = { showFilterSheet = false },
        dueWithin = !showComplied,
        timeNote = if (showComplied) null else stringResource(
          SearchRes.string.meter_task_note,
          LexiconFormatter.sentenceCasePlural(taskNoun),
        ),
      )
    }

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }
}
