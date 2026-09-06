package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
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
    SearchField("component_serial", item.squawk.component_serial, weight = 4),
    SearchField("title", item.squawk.title, weight = 3),
    SearchField("description", item.squawk.description, weight = 1),
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
}

/** Tasks: active ones read a preset as “due within”; complied ones date from compliance. A meter-only task has no date and always shows. */
class TaskAdapter : RecordAdapter<MaintenanceTaskWithStatus> {

  override fun fields(item: MaintenanceTaskWithStatus): List<SearchField> =
    listOf(
      SearchField("reference_number", item.card.reference_number, weight = 4),
      SearchField("title", item.card.title, weight = 3),
      SearchField("notes", item.card.notes, weight = 1),
      SearchField(
        "compliance_authority",
        item.card.compliance_authority,
        weight = 1
      ),
      SearchField(
        "compliance_details",
        item.card.compliance_details,
        weight = 1
      ),
    )

  override fun component(item: MaintenanceTaskWithStatus): ComponentType =
    item.card.component

  override fun date(item: MaintenanceTaskWithStatus): LocalDate? =
    if (item.isComplied) item.dueStatus.compliedDate else item.dueStatus.nextDueDate

  override fun direction(item: MaintenanceTaskWithStatus): TimeDirection =
    if (item.isComplied) TimeDirection.PAST else TimeDirection.FUTURE

  override val nullDateMatches: Boolean get() = true

  private val MaintenanceTaskWithStatus.isComplied get() = dueStatus.status == DueStatus.COMPLIED
}
