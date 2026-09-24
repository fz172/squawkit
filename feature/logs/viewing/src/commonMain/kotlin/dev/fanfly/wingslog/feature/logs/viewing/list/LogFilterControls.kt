package dev.fanfly.wingslog.feature.logs.viewing.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.search.datamanager.LogAdapter
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.countByComponent
import dev.fanfly.wingslog.feature.search.model.countByTime
import dev.fanfly.wingslog.feature.search.viewing.ChoiceChip
import dev.fanfly.wingslog.feature.search.viewing.FacetOption
import dev.fanfly.wingslog.feature.search.viewing.FacetPickerPage
import dev.fanfly.wingslog.feature.search.viewing.FilterSection
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterControls
import dev.fanfly.wingslog.thing.ComponentType
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.filter_all_people
import wingslog.feature.search.sharedassets.generated.resources.filter_q_when_happened
import wingslog.feature.search.sharedassets.generated.resources.filter_q_who_signed
import wingslog.feature.search.sharedassets.generated.resources.filter_q_worked_on
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

/** The filter sheet (a panel on wide tiers) with the "who signed" facet the log list adds to it. */
@Composable
internal fun LogFilterControls(
  expanded: Boolean,
  onDismiss: () -> Unit,
  filter: RecordFilter,
  uiState: MaintenanceLogListUiState.Success,
  onComponentFilterToggle: (ComponentType) -> Unit,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onFacetToggle: (Facet) -> Unit,
  onClearFilter: () -> Unit,
) {
  // Chip counts are measured against the tab's whole list with the same adapter the search uses,
  // so "Airframe 18" means the same thing the filter will.
  val zone = remember { TimeZone.currentSystemDefault() }
  val countAdapter = remember(zone) { LogAdapter(zone) }
  val today = remember {
    Clock.System.now()
      .toLocalDateTime(zone).date
  }
  var showPeoplePicker by remember { mutableStateOf(false) }
  var peopleQuery by remember { mutableStateOf("") }
  val logNounPlural =
    LexiconFormatter.plural(LocalThingLexicon.current.logNoun)
  val allLogs = uiState.allLogs
  val technicianNoun = LocalThingLexicon.current.technicianNoun
  val technicianPlural = LexiconFormatter.plural(technicianNoun)
  // Ordered by who signed the most recent log here, so "recent" means recent on this
  // thing rather than whoever happens to sort first.
  val people = remember(allLogs, filter.facets) {
    allLogs.mapNotNull { it.technician?.name?.takeIf(String::isNotBlank) }
      .distinct()
      .map { name ->
        FacetOption(
          name = name,
          count = allLogs.count { it.technician?.name == name },
          selected = Facet.Technician(name) in filter.facets,
        )
      }
  }
  val recentPeople = remember(allLogs, people) {
    val order = allLogs.mapNotNull { it.technician?.name }
      .distinct()
    people.sortedBy {
      order.indexOf(it.name)
        .takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
    }
  }
  // Anyone already chosen is promoted into a chip slot, so a selection is never hidden
  // behind "All 24 people" where it cannot be seen or undone.
  val quickPeople = remember(recentPeople) {
    (recentPeople.filter { it.selected } + recentPeople).distinct()
      .take(2)
  }
  RecordFilterControls(
    expanded = expanded,
    inline = LocalLayoutTier.current.hasSideNav,
    scopeLabel = logNounPlural,
    filter = filter,
    showComponentFilter = componentTypesApply,
    componentQuestion = stringResource(SearchRes.string.filter_q_worked_on),
    componentLabel = { it.displayName() },
    onComponentToggle = onComponentFilterToggle,
    timeQuestion = stringResource(SearchRes.string.filter_q_when_happened),
    onTimeWindowChange = onTimeWindowChange,
    onClear = { onClearFilter() },
    onDismiss = onDismiss,
    resultCount = uiState.logs.size,
    totalCount = uiState.totalCount,
    nounSingular = LocalThingLexicon.current.logNoun.singular,
    nounPlural = logNounPlural,
    componentCount = { c ->
      uiState.allLogs.countByComponent(
        countAdapter,
        c
      )
    },
    timeCount = { w ->
      uiState.allLogs.countByTime(
        countAdapter,
        w,
        today
      )
    },
    facetSection = if (people.isEmpty()) null else {
      {
        FilterSection(
          stringResource(SearchRes.string.filter_q_who_signed),
          pickOne = false,
        ) {
          // Two chips, whatever the roster does; the rest live behind the picker.
          quickPeople.forEach { option ->
            val facet = Facet.Technician(option.name)
            ChoiceChip(
              label = option.name,
              selected = option.selected,
              count = option.count,
              onClick = { onFacetToggle(facet) },
            )
          }
          if (people.size > quickPeople.size) {
            ChoiceChip(
              label = stringResource(
                SearchRes.string.filter_all_people,
                people.size,
                technicianPlural,
              ),
              selected = false,
              onClick = { showPeoplePicker = true },
            )
          }
        }
      }
    },
    page = if (!showPeoplePicker) null else {
      {
        FacetPickerPage(
          title = stringResource(SearchRes.string.filter_q_who_signed),
          options = people,
          recent = recentPeople,
          nounSingular = technicianNoun.singular,
          nounPlural = technicianPlural,
          query = peopleQuery,
          onQueryChange = { peopleQuery = it },
          onToggle = { onFacetToggle(Facet.Technician(it.name)) },
          onBack = {
            peopleQuery = ""
            showPeoplePicker = false
          },
        )
      }
    },
  )
}
