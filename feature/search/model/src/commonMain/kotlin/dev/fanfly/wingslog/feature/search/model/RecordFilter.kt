package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.SquawkPriority

/** What one list tab is narrowed to. Held per tab in its ViewModel. */
data class RecordFilter(
  val query: String = "",
  val components: Set<ComponentType> = emptySet(),
  val time: TimeWindow = TimeWindow.All,
  /** All of one kind on a tab; several OR together, like [components]. */
  val facets: Set<Facet> = emptySet(),
) {
  /** Anything other than the query — what the filter button’s indicator reflects. */
  val hasNonQueryFilter: Boolean
    get() = components.isNotEmpty() || time != TimeWindow.All || facets.isNotEmpty()

  val isActive: Boolean get() = query.isNotBlank() || hasNonQueryFilter

  fun toggleComponent(component: ComponentType): RecordFilter =
    copy(components = if (component in components) components - component else components + component)

  fun toggleFacet(facet: Facet): RecordFilter =
    copy(facets = if (facet in facets) facets - facet else facets + facet)

  fun withoutFilters(): RecordFilter =
    copy(components = emptySet(), time = TimeWindow.All, facets = emptySet())
}

/** The tab-specific filter a sheet offers; one kind per tab. */
sealed interface Facet {
  data class Priority(val value: SquawkPriority) : Facet
  data class Compliance(val value: ComplianceType) : Facet
  data class Technician(val name: String) : Facet
}
