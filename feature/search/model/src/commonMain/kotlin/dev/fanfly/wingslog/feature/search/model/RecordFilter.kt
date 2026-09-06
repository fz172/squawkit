package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.SquawkPriority

/** What one list tab is narrowed to. Held per tab in its ViewModel. */
data class RecordFilter(
  val query: String = "",
  val components: Set<ComponentType> = emptySet(),
  val time: TimeWindow = TimeWindow.All,
  val facet: Facet? = null,
) {
  /** Anything other than the query — what the filter button’s indicator reflects. */
  val hasNonQueryFilter: Boolean
    get() = components.isNotEmpty() || time != TimeWindow.All || facet != null

  val isActive: Boolean get() = query.isNotBlank() || hasNonQueryFilter

  fun toggleComponent(component: ComponentType): RecordFilter =
    copy(components = if (component in components) components - component else components + component)

  fun withoutFilters(): RecordFilter =
    copy(components = emptySet(), time = TimeWindow.All, facet = null)
}

/** The one tab-specific filter a sheet offers. */
sealed interface Facet {
  data class Priority(val value: SquawkPriority) : Facet
  data class Compliance(val value: ComplianceType) : Facet
  data class Technician(val name: String) : Facet
}
