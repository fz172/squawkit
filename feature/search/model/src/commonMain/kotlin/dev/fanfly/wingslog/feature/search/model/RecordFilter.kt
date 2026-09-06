package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.SquawkPriority

/**
 * What one list tab is narrowed to: a free-text query plus the structured filters
 * (`docs/search/search_filter_design.md` §3). A value, held per tab in that tab’s ViewModel.
 */
data class RecordFilter(
  val query: String = "",
  val components: Set<ComponentType> = emptySet(),
  val time: TimeWindow = TimeWindow.All,
  val facet: Facet? = null,
) {
  /** True when anything other than the query narrows the list — what the filter button indicates. */
  val hasNonQueryFilter: Boolean
    get() = components.isNotEmpty() || time != TimeWindow.All || facet != null

  val isActive: Boolean get() = query.isNotBlank() || hasNonQueryFilter

  fun toggleComponent(component: ComponentType): RecordFilter =
    copy(components = if (component in components) components - component else components + component)

  /** Everything but the query back to its default. */
  fun withoutFilters(): RecordFilter = copy(components = emptySet(), time = TimeWindow.All, facet = null)
}

/** The one tab-specific choice a filter sheet offers (PRD FR.20–22). Exactly one kind per tab. */
sealed interface Facet {
  data class Priority(val value: SquawkPriority) : Facet
  data class Compliance(val value: ComplianceType) : Facet
  data class Technician(val name: String) : Facet
}
