package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class LayoutMemoryCodecTest {

  private fun catalogue(vararg columns: Int) = columns.map {
    DataLogSeries(
      column = it,
      kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC
    )
  }

  private val layout = ChartLayout(
    panes = listOf(
      Pane(PaneId(0), listOf(SeriesKey(3), SeriesKey(5))),
      Pane(PaneId(1), listOf(SeriesKey(7))),
    ),
    targetPane = PaneId(1),
  )

  @Test
  fun aRoundTripKeepsThePanesTheTargetAndTheAxis() {
    val encoded =
      LayoutMemoryCodec.encode(LayoutMemory(layout, clockAxis = true))

    val decoded = LayoutMemoryCodec.decode(encoded, catalogue(3, 5, 7))!!

    assertThat(decoded.clockAxis).isTrue()
    assertThat(decoded.layout.panes.map { it.series }).containsExactly(
      listOf(SeriesKey(3), SeriesKey(5)),
      listOf(SeriesKey(7)),
    )
      .inOrder()
    assertThat(decoded.layout.targetPane).isEqualTo(decoded.layout.panes[1].id)
  }

  @Test
  fun columnsTheLogNoLongerHasAreDropped() {
    val encoded =
      LayoutMemoryCodec.encode(LayoutMemory(layout, clockAxis = false))

    // Column 5 is gone and column 7 was never numeric: the second pane empties and disappears.
    val decoded = LayoutMemoryCodec.decode(encoded, catalogue(3))!!

    assertThat(decoded.clockAxis).isFalse()
    assertThat(decoded.layout.panes.map { it.series }).containsExactly(
      listOf(
        SeriesKey(3)
      )
    )
    assertThat(decoded.layout.targetPane).isEqualTo(decoded.layout.panes.single().id)
  }

  @Test
  fun nothingLeftOrNotOursDecodesToNull() {
    val encoded =
      LayoutMemoryCodec.encode(LayoutMemory(layout, clockAxis = false))

    assertThat(LayoutMemoryCodec.decode(encoded, catalogue(11))).isNull()
    assertThat(
      LayoutMemoryCodec.decode(
        "v2;c=1;t=0;p=3",
        catalogue(3)
      )
    ).isNull()
    assertThat(LayoutMemoryCodec.decode("", catalogue(3))).isNull()
    assertThat(LayoutMemoryCodec.decode("garbage", catalogue(3))).isNull()
  }

  @Test
  fun panesAreRenumberedFromZeroSoTheIdsStayDense() {
    val sparse = ChartLayout(
      panes = listOf(
        Pane(PaneId(4), listOf(SeriesKey(3))),
        Pane(PaneId(9), listOf(SeriesKey(5)))
      ),
      targetPane = PaneId(9),
    )

    val decoded = LayoutMemoryCodec.decode(
      LayoutMemoryCodec.encode(LayoutMemory(sparse, clockAxis = false)),
      catalogue(3, 5),
    )!!

    assertThat(decoded.layout.panes.map { it.id }).containsExactly(
      PaneId(0),
      PaneId(1)
    )
      .inOrder()
    assertThat(decoded.layout.targetPane).isEqualTo(PaneId(1))
  }
}
