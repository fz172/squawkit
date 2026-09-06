package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class RecordAdaptersTest {

  private fun at(date: String) =
    toWireInstant(Instant.parse("${date}T12:00:00Z").epochSeconds)

  private val squawk = Squawk(
    id = "s1",
    title = "Transponder intermittent",
    description = "Drops out on climb",
    component_type = ComponentType.COMPONENT_AIRFRAME,
    component_serial = "3AB012345",
    created_at = at("2026-08-22"),
  )

  @Test
  fun squawk_fieldsAndComponent() {
    val adapter = SquawkAdapter(TimeZone.UTC)
    val item = SquawkWithStatus(squawk, SquawkStatus.OPEN)
    assertThat(
      adapter.fields(item)
        .map { it.name }).containsExactly(
      "component_serial",
      "title",
      "description"
    )
      .inOrder()
    assertThat(adapter.component(item)).isEqualTo(ComponentType.COMPONENT_AIRFRAME)
    assertThat(adapter.direction(item)).isEqualTo(TimeDirection.PAST)
    assertThat(adapter.nullDateMatches).isFalse()
  }

  @Test
  fun squawk_dateFollowsStatus() {
    val adapter = SquawkAdapter(
      TimeZone.UTC,
      logDates = mapOf("l9" to LocalDate(2026, 8, 25))
    )
    assertThat(
      adapter.date(
        SquawkWithStatus(
          squawk,
          SquawkStatus.OPEN
        )
      )
    ).isEqualTo(LocalDate(2026, 8, 22))

    val dismissed = squawk.copy(dismissed_at = at("2026-09-01"))
    assertThat(
      adapter.date(
        SquawkWithStatus(
          dismissed,
          SquawkStatus.DISMISSED
        )
      )
    ).isEqualTo(LocalDate(2026, 9, 1))

    val addressed = squawk.copy(addressed_by_log_id = "l9")
    assertThat(
      adapter.date(
        SquawkWithStatus(
          addressed,
          SquawkStatus.ADDRESSED
        )
      )
    ).isEqualTo(LocalDate(2026, 8, 25))

    val addressedUnknownLog = squawk.copy(addressed_by_log_id = "gone")
    assertThat(
      adapter.date(
        SquawkWithStatus(
          addressedUnknownLog,
          SquawkStatus.ADDRESSED
        )
      )
    ).isEqualTo(LocalDate(2026, 8, 22))
  }

  private val card = MaintenanceTask(
    id = "t1",
    title = "Transponder and altimeter test",
    notes = "Two-year IFR certification",
    reference_number = "91.413",
    component = ComponentType.COMPONENT_AIRFRAME,
  )

  @Test
  fun task_fieldsAndComponent() {
    val adapter = TaskAdapter()
    val item = MaintenanceTaskWithStatus(card, DueMetadata())
    assertThat(
      adapter.fields(item)
        .map { it.name })
      .containsExactly(
        "reference_number",
        "title",
        "notes",
        "compliance_authority",
        "compliance_details"
      )
      .inOrder()
    assertThat(adapter.component(item)).isEqualTo(ComponentType.COMPONENT_AIRFRAME)
    assertThat(adapter.nullDateMatches).isTrue()
  }

  @Test
  fun task_activeLooksForwardToDue_compliedLooksBack() {
    val adapter = TaskAdapter()
    val active = MaintenanceTaskWithStatus(
      card,
      DueMetadata(nextDueDate = LocalDate(2027, 5, 20))
    )
    assertThat(adapter.date(active)).isEqualTo(LocalDate(2027, 5, 20))
    assertThat(adapter.direction(active)).isEqualTo(TimeDirection.FUTURE)

    val meterOnly =
      MaintenanceTaskWithStatus(card, DueMetadata(nextDueEngine = 2889f))
    assertThat(adapter.date(meterOnly)).isNull()

    val complied = MaintenanceTaskWithStatus(
      card,
      DueMetadata(
        status = DueStatus.COMPLIED,
        compliedDate = LocalDate(2026, 3, 14)
      ),
    )
    assertThat(adapter.date(complied)).isEqualTo(LocalDate(2026, 3, 14))
    assertThat(adapter.direction(complied)).isEqualTo(TimeDirection.PAST)
  }
}
