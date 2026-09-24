package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.countByComponent
import dev.fanfly.wingslog.feature.search.model.countByTime
import dev.fanfly.wingslog.feature.search.viewing.ChoiceChip
import dev.fanfly.wingslog.feature.search.viewing.FilterSection
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterBar
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterControls
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.model.TaskAdapter
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.filter_q_due_before
import wingslog.feature.search.sharedassets.generated.resources.filter_q_part_of
import wingslog.feature.search.sharedassets.generated.resources.filter_q_when_happened
import wingslog.feature.search.sharedassets.generated.resources.filter_q_where_from
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_completed
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_due
import wingslog.feature.search.sharedassets.generated.resources.meter_task_note
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

/** The search bar and filter sheet over the task list, counting against the unfiltered sub-view. */
@Composable
internal fun TaskFilterBar(
  state: ThingOverviewUiState.Success,
  filter: RecordFilter,
  onFilterChange: (RecordFilter) -> Unit,
  showComplied: Boolean,
  activeTasks: List<MaintenanceTaskWithStatus>,
  completedTasks: List<MaintenanceTaskWithStatus>,
) {
  val taskFilter = filter
  val setFilter = onFilterChange
  val taskNoun = LocalThingLexicon.current.taskNoun
  var showFilterSheet by remember { mutableStateOf(false) }
  // Unfiltered, so a chip's count does not move every time another chip is tapped.
  val subView = if (showComplied) state.completedTasks else state.activeTasks
  val countAdapter = remember { TaskAdapter() }
  val today = remember {
    Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault()).date
  }
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
    facetLabel = {
      (it as? Facet.Compliance)?.let { c -> complianceLabel(c.value) }
        .orEmpty()
    },
    onRemoveFacet = { setFilter(taskFilter.toggleFacet(it)) },
  )
  RecordFilterControls(
    expanded = showFilterSheet,
    inline = LocalLayoutTier.current.hasSideNav,
    scopeLabel = if (showComplied) {
      stringResource(
        SearchRes.string.filter_scope_completed,
        taskNoun.plural
      )
    } else {
      stringResource(SearchRes.string.filter_scope_due, taskNoun.plural)
    },
    filter = taskFilter,
    showComponentFilter = componentTypesApply,
    // The thing's own noun: "Which part of the aircraft" on a plane, "of the car" on a car.
    componentQuestion = stringResource(
      SearchRes.string.filter_q_part_of,
      LocalThingLexicon.current.thingNoun.singular,
    ),
    componentLabel = { it.displayName() },
    onComponentToggle = { setFilter(taskFilter.toggleComponent(it)) },
    timeQuestion = stringResource(
      if (showComplied) SearchRes.string.filter_q_when_happened else SearchRes.string.filter_q_due_before
    ),
    onTimeWindowChange = { setFilter(taskFilter.copy(time = it)) },
    onClear = { setFilter(taskFilter.withoutFilters()) },
    onDismiss = { showFilterSheet = false },
    resultCount = (if (showComplied) completedTasks else activeTasks).size,
    totalCount = subView.size,
    nounSingular = taskNoun.singular,
    nounPlural = taskNoun.plural,
    componentCount = { c -> subView.countByComponent(countAdapter, c) },
    timeCount = { w -> subView.countByTime(countAdapter, w, today) },
    dueWithin = !showComplied,
    timeNote = if (showComplied) null else stringResource(
      SearchRes.string.meter_task_note,
      LexiconFormatter.sentenceCasePlural(taskNoun),
    ),
    horizontalPadding = Spacing.none,
    facetSection = {
      FilterSection(
        stringResource(SearchRes.string.filter_q_where_from),
        pickOne = false,
      ) {
        COMPLIANCE_OPTIONS.forEach { type ->
          val facet = Facet.Compliance(type)
          ChoiceChip(
            label = complianceLabel(type),
            selected = facet in taskFilter.facets,
            count = subView.count { it.card.type == type },
            onClick = { setFilter(taskFilter.toggleFacet(facet)) },
          )
        }
      }
    },
  )
}
