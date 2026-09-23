package dev.fanfly.wingslog.feature.squawk.model

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Squawks: open ones date from creation, closed ones from when they were addressed or dismissed. */
class SquawkAdapter(
  private val timeZone: TimeZone,
  /** Log id → work date, so an addressed squawk can date from the log that closed it. */
  private val logDates: Map<String, LocalDate> = emptyMap(),
) : RecordAdapter<SquawkWithStatus> {

  override fun fields(item: SquawkWithStatus): List<SearchField> = listOf(
    SearchField(FIELD_SERIAL, item.squawk.component_serial, weight = 4),
    SearchField(FIELD_TITLE, item.squawk.title, weight = 3),
    SearchField(FIELD_DESCRIPTION, item.squawk.description, weight = 1),
  )

  override fun component(item: SquawkWithStatus): ComponentType =
    item.squawk.component_type

  override fun date(item: SquawkWithStatus): LocalDate? {
    val created = item.squawk.created_at?.toLocalDate(timeZone)
    return when (item.status) {
      SquawkStatus.OPEN -> created
      SquawkStatus.DISMISSED -> item.squawk.dismissed_at?.toLocalDate(timeZone)
        ?: created

      SquawkStatus.ADDRESSED -> logDates[item.squawk.addressed_by_log_id]
        ?: created
    }
  }

  override fun facetMatches(item: SquawkWithStatus, facet: Facet): Boolean =
    facet is Facet.Priority && item.squawk.priority == facet.value

  companion object {
    const val FIELD_SERIAL = "component_serial"
    const val FIELD_TITLE = "title"
    const val FIELD_DESCRIPTION = "description"
  }
}
