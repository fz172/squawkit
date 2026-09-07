package dev.fanfly.wingslog.feature.search.datamanager

import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.search.model.RecordAdapter
import dev.fanfly.wingslog.feature.search.model.SearchField
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Work logs: searched by serial, description and technician; filtered by work date. */
class LogAdapter(
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : RecordAdapter<MaintenanceLog> {

  override fun fields(item: MaintenanceLog): List<SearchField> = listOf(
    SearchField(FIELD_SERIAL, item.component_serial, weight = 4),
    SearchField(FIELD_DESCRIPTION, item.work_description, weight = 1),
    SearchField(FIELD_TECHNICIAN, item.technician?.name.orEmpty(), weight = 1),
  )

  override fun component(item: MaintenanceLog): ComponentType =
    item.component_type

  override fun date(item: MaintenanceLog): LocalDate? =
    item.timestamp?.toLocalDate(timeZone)

  companion object {
    const val FIELD_SERIAL = "component_serial"
    const val FIELD_DESCRIPTION = "work_description"
    const val FIELD_TECHNICIAN = "technician"
  }
}
