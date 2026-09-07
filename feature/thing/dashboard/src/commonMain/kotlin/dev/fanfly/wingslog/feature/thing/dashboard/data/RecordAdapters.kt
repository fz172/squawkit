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

  companion object {
    const val FIELD_SERIAL = "component_serial"
    const val FIELD_TITLE = "title"
    const val FIELD_DESCRIPTION = "description"
  }
}

/** Tasks: active ones read a preset as “due within”; complied ones date from compliance. A meter-only task has no date and always shows. */
class TaskAdapter : RecordAdapter<MaintenanceTaskWithStatus> {

  override fun fields(item: MaintenanceTaskWithStatus): List<SearchField> =
    listOf(
      SearchField(FIELD_REFERENCE, item.card.reference_number, weight = 4),
      SearchField(FIELD_TITLE, item.card.title, weight = 3),
      SearchField(FIELD_NOTES, item.card.notes, weight = 1),
      SearchField(FIELD_AUTHORITY,
        item.card.compliance_authority,
        weight = 1
      ),
      SearchField(FIELD_DETAILS,
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

  companion object {
    const val FIELD_REFERENCE = "reference_number"
    const val FIELD_TITLE = "title"
    const val FIELD_NOTES = "notes"
    const val FIELD_AUTHORITY = "compliance_authority"
    const val FIELD_DETAILS = "compliance_details"
  }
}
