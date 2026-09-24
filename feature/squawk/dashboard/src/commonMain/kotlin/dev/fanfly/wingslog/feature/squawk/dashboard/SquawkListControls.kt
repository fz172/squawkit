package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.bar.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.countByTime
import dev.fanfly.wingslog.feature.search.viewing.ChoiceChip
import dev.fanfly.wingslog.feature.search.viewing.FilterSection
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterBar
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterControls
import dev.fanfly.wingslog.feature.squawk.model.SquawkAdapter
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.filter_q_how_urgent
import wingslog.feature.search.sharedassets.generated.resources.filter_q_when_reported
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_closed
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_open
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.closed_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.open_with_count
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

/** Search, filters, the Open/Closed toggle and the count row that head the squawk list. */
@Composable
internal fun SquawkListControls(
  state: ThingOverviewUiState.Success,
  filter: RecordFilter,
  onFilterChange: (RecordFilter) -> Unit,
  onClearFilter: () -> Unit,
  showClosed: Boolean,
  onShowClosedChange: (Boolean) -> Unit,
  openSquawks: List<SquawkWithStatus>,
  closedSquawks: List<SquawkWithStatus>,
) {
  val analytics = LocalAnalytics.current
  val squawkNoun = LocalThingLexicon.current.squawkNoun
  val squawkFilter = filter
  val setFilter = onFilterChange
  val displayList = if (showClosed) closedSquawks else openSquawks
  var showFilterSheet by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    RecordFilterBar(
      filter = squawkFilter,
      placeholder = stringResource(SearchRes.string.search_placeholder),
      // Squawks are filed against the thing, not a component, so the section would be dead.
      showComponentFilter = false,
      componentLabel = { it.displayName() },
      onQueryChange = { setFilter(squawkFilter.copy(query = it)) },
      onOpenFilters = { showFilterSheet = true },
      onRemoveComponent = { setFilter(squawkFilter.toggleComponent(it)) },
      onClearTime = { setFilter(squawkFilter.copy(time = TimeWindow.All)) },
      horizontalPadding = Spacing.none,
      facetLabel = {
        (it as? Facet.Priority)?.let { p -> priorityLabel(p.value) }
          .orEmpty()
      },
      onRemoveFacet = { setFilter(squawkFilter.toggleFacet(it)) },
    )
    // Unfiltered, so a chip's count does not move every time another chip is tapped.
    val countAdapter =
      remember { SquawkAdapter(TimeZone.currentSystemDefault()) }
    val today = remember {
      Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val subView = if (showClosed) {
      state.squawks.filter { it.status != SquawkStatus.OPEN }
    } else {
      state.squawks.filter { it.status == SquawkStatus.OPEN }
    }
    RecordFilterControls(
      expanded = showFilterSheet,
      inline = LocalLayoutTier.current.hasSideNav,
      scopeLabel = if (showClosed) {
        stringResource(
          SearchRes.string.filter_scope_closed,
          squawkNoun.plural
        )
      } else {
        stringResource(
          SearchRes.string.filter_scope_open,
          squawkNoun.plural
        )
      },
      filter = squawkFilter,
      // Squawks are filed against the thing, not a component, so the section would be dead.
      showComponentFilter = false,
      componentQuestion = "",
      componentLabel = { it.displayName() },
      onComponentToggle = { setFilter(squawkFilter.toggleComponent(it)) },
      timeQuestion = stringResource(SearchRes.string.filter_q_when_reported),
      onTimeWindowChange = { setFilter(squawkFilter.copy(time = it)) },
      onClear = { setFilter(squawkFilter.withoutFilters()) },
      onDismiss = { showFilterSheet = false },
      resultCount = (if (showClosed) closedSquawks else openSquawks).size,
      totalCount = subView.size,
      nounSingular = squawkNoun.singular,
      nounPlural = squawkNoun.plural,
      timeCount = { window ->
        subView.countByTime(
          countAdapter,
          window,
          today
        )
      },
      horizontalPadding = Spacing.none,
      facetSection = {
        FilterSection(
          stringResource(SearchRes.string.filter_q_how_urgent),
          pickOne = false
        ) {
          SQUAWK_TIERS.forEach { priority ->
            val facet = Facet.Priority(priority)
            ChoiceChip(
              label = priorityLabel(priority),
              selected = facet in squawkFilter.facets,
              count = subView.count { it.squawk.priority == priority },
              onClick = { setFilter(squawkFilter.toggleFacet(facet)) },
            )
          }
        }
      },
    )

    DualSegmentedFilter(
      option1 = stringResource(
        Res.string.open_with_count,
        openSquawks.size
      ),
      option2 = stringResource(
        Res.string.closed_with_count,
        closedSquawks.size
      ),
      selectedIndex = if (showClosed) 1 else 0,
      onSelect = {
        onShowClosedChange(it == 1)
        analytics.logScreenView("shell/squawks/${if (it == 1) "closed" else "open"}")
      },
    )

    RecordCountRow(
      count = displayList.size,
      nounSingular = squawkNoun.singular,
      nounPlural = squawkNoun.plural,
      filterActive = squawkFilter.isActive,
      onClear = onClearFilter,
      horizontalPadding = Spacing.none,
    )
  }
}
