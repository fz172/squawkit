package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.Thing
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test

class RecordFiltersTest {

  private val today = LocalDate(2026, 9, 6)
  private fun at(date: String) = toWireInstant(Instant.parse("${date}T12:00:00Z").epochSeconds)

  private val openXpdr = SquawkWithStatus(
    Squawk(id = "s1", title = "Transponder intermittent", component_type = ComponentType.COMPONENT_AIRFRAME, created_at = at("2026-08-22")),
    SquawkStatus.OPEN,
  )
  private val openOil = SquawkWithStatus(
    Squawk(id = "s2", title = "Oil seep at magneto", component_type = ComponentType.COMPONENT_ENGINE, created_at = at("2026-08-30")),
    SquawkStatus.OPEN,
  )
  private val addressedElt = SquawkWithStatus(
    Squawk(id = "s3", title = "ELT self-test fails", component_type = ComponentType.COMPONENT_AIRFRAME, created_at = at("2025-04-02"), addressed_by_log_id = "l5"),
    SquawkStatus.ADDRESSED,
  )
  private val eltLog = MaintenanceLog(id = "l5", timestamp = at("2026-04-05"), work_description = "Replaced ELT battery")

  private val dueSoon = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t1", title = "ELT battery replacement", component = ComponentType.COMPONENT_AIRFRAME),
    DueMetadata(nextDueDate = LocalDate(2026, 10, 1)),
  )
  private val dueLater = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t2", title = "Annual inspection", component = ComponentType.COMPONENT_AIRFRAME),
    DueMetadata(nextDueDate = LocalDate(2027, 3, 14)),
  )
  private val meterOnly = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t3", title = "Oil and filter change", component = ComponentType.COMPONENT_ENGINE),
    DueMetadata(nextDueEngine = 2889f),
  )
  private val complied = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t4", title = "Fuel selector AD", reference_number = "AD 2011-10-09", component = ComponentType.COMPONENT_AIRFRAME),
    DueMetadata(status = DueStatus.COMPLIED, compliedDate = LocalDate(2026, 3, 14)),
  )

  private val base = ThingOverviewUiState.Success(
    thing = Thing(id = "thing-1"),
    activeTasks = listOf(dueSoon, dueLater, meterOnly),
    completedTasks = listOf(complied),
    squawks = listOf(openXpdr, openOil, addressedElt),
  )

  private fun apply(squawkFilter: RecordFilter = RecordFilter(), taskFilter: RecordFilter = RecordFilter()) =
    base.applyFilters(squawkFilter, taskFilter, SearchEngineImpl(), listOf(eltLog), today, TimeZone.UTC)

  @Test
  fun noFilter_mirrorsRawLists() {
    val s = apply()
    assertThat(s.filteredSquawks).isEqualTo(base.squawks)
    assertThat(s.filteredActiveTasks).isEqualTo(base.activeTasks)
    assertThat(s.filteredCompletedTasks).isEqualTo(base.completedTasks)
  }

  @Test
  fun squawkFilter_leavesRawListsAndTasksAlone() {
    val s = apply(squawkFilter = RecordFilter(query = "transponder"))
    assertThat(s.filteredSquawks.map { it.squawk.id }).containsExactly("s1")
    assertThat(s.squawks).hasSize(3)
    assertThat(s.filteredActiveTasks).hasSize(3)
    assertThat(s.squawkFilter.query).isEqualTo("transponder")
  }

  @Test
  fun addressedSquawk_datesFromItsLog() {
    val s = apply(squawkFilter = RecordFilter(time = TimeWindow.LastMonths(12)))
    // Created 2025-04, addressed 2026-04: inside the window only because the log date is used.
    assertThat(s.filteredSquawks.map { it.squawk.id }).containsExactly("s1", "s2", "s3")
    val threeMonths = apply(squawkFilter = RecordFilter(time = TimeWindow.LastMonths(3)))
    assertThat(threeMonths.filteredSquawks.map { it.squawk.id }).containsExactly("s1", "s2")
  }

  @Test
  fun taskWindow_isDueWithinForActive_andPastForComplied() {
    val s = apply(taskFilter = RecordFilter(time = TimeWindow.LastMonths(3)))
    assertThat(s.filteredActiveTasks.map { it.card.id }).containsExactly("t1", "t3")
    assertThat(s.filteredCompletedTasks).isEmpty()

    val year = apply(taskFilter = RecordFilter(time = TimeWindow.LastMonths(12)))
    assertThat(year.filteredActiveTasks.map { it.card.id }).containsExactly("t1", "t2", "t3")
    assertThat(year.filteredCompletedTasks.map { it.card.id }).containsExactly("t4")
  }

  @Test
  fun taskComponentAndQuery() {
    val engine = apply(taskFilter = RecordFilter(components = setOf(ComponentType.COMPONENT_ENGINE)))
    assertThat(engine.filteredActiveTasks.map { it.card.id }).containsExactly("t3")
    val ad = apply(taskFilter = RecordFilter(query = "2011-10-09"))
    assertThat(ad.filteredActiveTasks).isEmpty()
    assertThat(ad.filteredCompletedTasks.map { it.card.id }).containsExactly("t4")
  }
}
