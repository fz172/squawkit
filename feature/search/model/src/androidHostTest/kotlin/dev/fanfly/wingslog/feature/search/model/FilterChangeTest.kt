package dev.fanfly.wingslog.feature.search.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.SquawkPriority
import org.junit.Test

class FilterChangeTest {

  @Test
  fun describesEachStructuralChange_neverTheQueryOrAName() {
    val before = RecordFilter(
      query = "oil",
      components = setOf(ComponentType.COMPONENT_ENGINE)
    )
    val after = RecordFilter(
      query = "oil change",
      components = setOf(ComponentType.COMPONENT_AIRFRAME),
      time = TimeWindow.LastMonths(12),
      facets = setOf(
        Facet.Priority(SquawkPriority.SQUAWK_PRIORITY_AOG),
        Facet.Technician("R. Alvarez")
      ),
    )
    assertThat(after.changesFrom(before)).containsExactly(
      FilterChange("component", "airframe"),
      FilterChange("component", "-engine"),
      FilterChange("time", "12m"),
      FilterChange("facet", "priority:aog"),
      FilterChange("facet", "technician"),
    )
      .inOrder()
  }

  @Test
  fun clearingCollapsesToOneChange_andNoChangeIsEmpty() {
    val active = RecordFilter(
      components = setOf(ComponentType.COMPONENT_ENGINE),
      time = TimeWindow.LastMonths(3)
    )
    assertThat(RecordFilter().changesFrom(active)).containsExactly(
      FilterChange(
        "clear",
        "all"
      )
    )
    assertThat(RecordFilter(query = "x").changesFrom(RecordFilter())).isEmpty()
    assertThat(active.changesFrom(active)).isEmpty()
  }
}
