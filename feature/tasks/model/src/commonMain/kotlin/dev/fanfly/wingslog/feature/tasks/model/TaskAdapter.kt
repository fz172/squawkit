package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate

/** Tasks: active ones read a preset as “due within”; complied ones date from compliance. A meter-only task has no date and always shows. */
class TaskAdapter : RecordAdapter<MaintenanceTaskWithStatus> {

  override fun fields(item: MaintenanceTaskWithStatus): List<SearchField> =
    listOf(
      SearchField(FIELD_REFERENCE, item.card.reference_number, weight = 4),
      SearchField(FIELD_TITLE, item.card.title, weight = 3),
      SearchField(FIELD_NOTES, item.card.notes, weight = 1),
      SearchField(
        FIELD_AUTHORITY,
        item.card.compliance_authority,
        weight = 1
      ),
      SearchField(
        FIELD_DETAILS,
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

  override fun facetMatches(
    item: MaintenanceTaskWithStatus,
    facet: Facet
  ): Boolean =
    facet is Facet.Compliance && item.card.type == facet.value

  companion object {
    const val FIELD_REFERENCE = "reference_number"
    const val FIELD_TITLE = "title"
    const val FIELD_NOTES = "notes"
    const val FIELD_AUTHORITY = "compliance_authority"
    const val FIELD_DETAILS = "compliance_details"
  }
}
