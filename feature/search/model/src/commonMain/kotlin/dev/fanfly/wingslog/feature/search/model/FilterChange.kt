package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComponentType

/** One user-visible filter change, in analytics terms: never a technician’s name, never the query. */
data class FilterChange(val kind: String, val value: String)

/** What changed between [previous] and this filter, ignoring the query. */
fun RecordFilter.changesFrom(previous: RecordFilter): List<FilterChange> {
  if (!hasNonQueryFilter && previous.hasNonQueryFilter) return listOf(
    FilterChange("clear", "all")
  )
  val changes = ArrayList<FilterChange>()
  (components - previous.components).forEach {
    changes += FilterChange(
      "component",
      it.wireName()
    )
  }
  (previous.components - components).forEach {
    changes += FilterChange(
      "component",
      "-" + it.wireName()
    )
  }
  if (time != previous.time) changes += FilterChange("time", time.presetName())
  (facets - previous.facets).forEach {
    changes += FilterChange(
      "facet",
      it.analyticsValue()
    )
  }
  (previous.facets - facets).forEach {
    changes += FilterChange(
      "facet",
      "-" + it.analyticsValue()
    )
  }
  return changes
}

private fun ComponentType.wireName() = name.removePrefix("COMPONENT_")
  .lowercase()

private fun TimeWindow.presetName() = when (this) {
  TimeWindow.All -> "all"
  is TimeWindow.LastMonths -> "${months}m"
  is TimeWindow.Custom -> "custom"
}

private fun Facet.analyticsValue() = when (this) {
  is Facet.Priority -> "priority:" + value.name.removePrefix("SQUAWK_PRIORITY_")
    .lowercase()

  is Facet.Compliance -> "compliance:" + value.name.removePrefix("COMPLIANCE_TYPE_")
    .lowercase()

  is Facet.Technician -> "technician"
}
