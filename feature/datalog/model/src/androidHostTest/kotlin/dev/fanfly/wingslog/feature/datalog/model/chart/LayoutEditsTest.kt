package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class LayoutEditsTest {

  private val rpm = SeriesKey(1)
  private val map = SeriesKey(2)
  private val oil = SeriesKey(3)
  private val p0 = PaneId(0)
  private val p1 = PaneId(1)
  private val catalogue = mapOf(
    1 to DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
    2 to DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION),
    3 to DataLogSeries(column = 3, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
  )
  private val layout = ChartLayout(listOf(Pane(p0, listOf(rpm)), Pane(p1, listOf(oil))), targetPane = p0)

  @Test
  fun addIsIdempotentAndTargetsThePane() {
    val once = LayoutEdits.add(layout, p1, rpm)
    assertThat(once.panes[1].series).containsExactly(oil, rpm).inOrder()
    assertThat(once.targetPane).isEqualTo(p1)
    assertThat(LayoutEdits.add(once, p1, rpm)).isEqualTo(once)
  }

  @Test
  fun removeLeavesAnEmptyPaneInPlace() {
    val edited = LayoutEdits.remove(layout, p0, rpm)
    assertThat(edited.panes[0].series).isEmpty()
    assertThat(edited.panes).hasSize(2)
  }

  @Test
  fun moveBetweenChartPanesAndOntoItself() {
    val moved = LayoutEdits.move(layout, rpm, p0, p1, catalogue)
    assertThat(moved.panes[0].series).isEmpty()
    assertThat(moved.panes[1].series).containsExactly(oil, rpm).inOrder()
    assertThat(moved.targetPane).isEqualTo(p1)
    assertThat(LayoutEdits.move(layout, rpm, p0, p0, catalogue)).isEqualTo(layout)
  }

  @Test
  fun aMapSeriesDroppedOnAChartPaneSpawnsItsOwnPane() {
    val withMap = ChartLayout(listOf(Pane(p0, listOf(map)), Pane(p1, listOf(oil))), targetPane = p0)
    val moved = LayoutEdits.move(withMap, map, p0, p1, catalogue)
    assertThat(moved.panes.map { it.series }).containsExactly(emptyList<SeriesKey>(), listOf(oil), listOf(map)).inOrder()
    assertThat(moved.targetPane).isEqualTo(PaneId(2))
    // And a chart series dropped on the map pane spawns too.
    val back = LayoutEdits.move(moved, oil, p1, PaneId(2), catalogue)
    assertThat(back.panes.last().series).containsExactly(oil)
    assertThat(back.panes[2].series).containsExactly(map)
  }

  @Test
  fun spawnAppendsWithAFreshIdAndRemovePaneRetargets() {
    val spawned = LayoutEdits.spawn(layout, oil)
    assertThat(spawned.panes.last()).isEqualTo(Pane(PaneId(2), listOf(oil)))
    assertThat(spawned.targetPane).isEqualTo(PaneId(2))

    val dropped = LayoutEdits.removePane(spawned, PaneId(2))
    assertThat(dropped.panes.map { it.id }).containsExactly(p0, p1).inOrder()
    assertThat(dropped.targetPane).isEqualTo(p1)
    // Removing the target pane retargets to the nearest remaining pane.
    assertThat(LayoutEdits.removePane(dropped, p1).targetPane).isEqualTo(p0)
    assertThat(LayoutEdits.removePane(ChartLayout(listOf(Pane(p0, emptyList())), p0), p0).targetPane).isNull()
    // Ids are never reused within a session: after removing pane 2, the next spawn is 2 again only
    // because it is above the highest in use; removing pane 1 then spawning gives 2, not 1.
    assertThat(LayoutEdits.spawn(LayoutEdits.removePane(layout, p1)).panes.last().id).isEqualTo(p1)
    assertThat(LayoutEdits.target(layout, p1).targetPane).isEqualTo(p1)
    assertThat(LayoutEdits.target(layout, PaneId(9))).isEqualTo(layout)
  }
}
