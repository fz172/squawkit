package dev.fanfly.wingslog.feature.search.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Technician
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test

class LogAdapterTest {

  private val adapter = LogAdapter(TimeZone.UTC)

  private val log = MaintenanceLog(
    id = "l1",
    // 2026-08-25T23:30:00Z
    timestamp = toWireInstant(epochSeconds = 1_787_700_600L),
    work_description = "Installed GTX 335 transponder",
    component_type = ComponentType.COMPONENT_AIRFRAME,
    component_serial = "3AB012345",
    technician = Technician(id = "t1", name = "R. Alvarez"),
  )

  @Test
  fun fields_serialOutweighsDescriptionAndTechnician() {
    val fields = adapter.fields(log)
    assertThat(fields.map { it.name }).containsExactly(
      "component_serial",
      "work_description",
      "technician"
    )
      .inOrder()
    assertThat(fields.map { it.text }).containsExactly(
      "3AB012345",
      "Installed GTX 335 transponder",
      "R. Alvarez"
    )
      .inOrder()
    assertThat(fields.first().weight).isGreaterThan(fields.last().weight)
  }

  @Test
  fun fields_toleratesMissingTechnician() {
    assertThat(
      adapter.fields(log.copy(technician = null))
        .last().text
    ).isEmpty()
  }

  @Test
  fun component_andDate_inTheAdapterZone() {
    assertThat(adapter.component(log)).isEqualTo(ComponentType.COMPONENT_AIRFRAME)
    assertThat(adapter.date(log)).isEqualTo(LocalDate(2026, 8, 25))
    assertThat(LogAdapter(TimeZone.of("Pacific/Auckland")).date(log)).isEqualTo(
      LocalDate(2026, 8, 26)
    )
    assertThat(adapter.date(log.copy(timestamp = null))).isNull()
    assertThat(adapter.direction(log)).isEqualTo(TimeDirection.PAST)
    assertThat(adapter.nullDateMatches).isFalse()
  }
}
