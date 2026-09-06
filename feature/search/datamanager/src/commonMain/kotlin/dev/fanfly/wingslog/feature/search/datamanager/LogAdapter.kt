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
    SearchField("component_serial", item.component_serial, weight = 4),
    SearchField("work_description", item.work_description, weight = 1),
    SearchField("technician", item.technician?.name.orEmpty(), weight = 1),
  )

  override fun component(item: MaintenanceLog): ComponentType =
    item.component_type

  override fun date(item: MaintenanceLog): LocalDate? =
    item.timestamp?.toLocalDate(timeZone)
}
