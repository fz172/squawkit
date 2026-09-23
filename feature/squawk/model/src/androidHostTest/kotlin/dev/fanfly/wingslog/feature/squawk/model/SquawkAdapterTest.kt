package dev.fanfly.wingslog.feature.squawk.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.TimeDirection
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkPriority
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test

class SquawkAdapterTest {

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
  fun squawk_priorityFacet() {
    val adapter = SquawkAdapter(TimeZone.UTC)
    val high = SquawkWithStatus(
      squawk.copy(priority = SquawkPriority.SQUAWK_PRIORITY_HIGH),
      SquawkStatus.OPEN
    )
    assertThat(
      adapter.facetMatches(
        high,
        Facet.Priority(SquawkPriority.SQUAWK_PRIORITY_HIGH)
      )
    ).isTrue()
    assertThat(
      adapter.facetMatches(
        high,
        Facet.Priority(SquawkPriority.SQUAWK_PRIORITY_AOG)
      )
    ).isFalse()
    assertThat(adapter.facetMatches(high, Facet.Technician("x"))).isFalse()
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
}
