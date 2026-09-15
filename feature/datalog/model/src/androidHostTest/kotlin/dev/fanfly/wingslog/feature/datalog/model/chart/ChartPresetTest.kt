package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class ChartPresetTest {

  private fun s(column: Int, canonical: String) = DataLogSeries(
    column = column,
    kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
    canonical_id = canonical,
  )

  @Test
  fun aPresetKeepsOnlyTheSeriesTheLogCarries() {
    val catalogue = listOf(
      s(1, CanonicalSeries.engine(1, "rpm")),
      s(2, CanonicalSeries.engine(1, "oil_temp")),
      s(3, CanonicalSeries.engine(1, "cht", 1)),
      s(4, CanonicalSeries.engine(1, "cht", 2)),
      s(9, CanonicalSeries.IAS),
    )

    val layout = ChartPreset.ENGINE.resolve(catalogue)!!

    // MAP, oil pressure and every EGT are absent, so their slots collapse: RPM, oil temp, two CHTs.
    assertThat(layout.panes.map { pane -> pane.series }).containsExactly(
      listOf(SeriesKey(1)),
      listOf(SeriesKey(2)),
      listOf(SeriesKey(3), SeriesKey(4)),
    ).inOrder()
    assertThat(layout.panes.map { it.id }).containsExactly(PaneId(0), PaneId(1), PaneId(2)).inOrder()
    assertThat(layout.targetPane).isEqualTo(PaneId(0))
  }

  @Test
  fun aPresetTheLogHasNothingForResolvesToNull() {
    val catalogue = listOf(s(1, CanonicalSeries.engine(1, "rpm")))

    assertThat(ChartPreset.FLIGHT.resolve(catalogue)).isNull()
    assertThat(ChartPreset.ELECTRICAL.resolve(catalogue)).isNull()
    assertThat(ChartPreset.ENGINE.resolve(emptyList())).isNull()
  }

  @Test
  fun unknownColumnsAreNeverPresetMaterial() {
    // A column with no canonical id is still plottable by hand, but no preset can name it.
    val catalogue = listOf(DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC))

    assertThat(ChartPreset.entries.mapNotNull { it.resolve(catalogue) }).isEmpty()
  }

  @Test
  fun flightGroupsAltitudeSpeedAndVerticalSpeed() {
    val catalogue = listOf(
      s(1, CanonicalSeries.ALT_BARO),
      s(2, CanonicalSeries.IAS),
      s(3, CanonicalSeries.GROUND_SPEED),
      s(4, CanonicalSeries.VERTICAL_SPEED),
    )

    val panes = ChartPreset.FLIGHT.resolve(catalogue)!!.panes

    assertThat(panes.map { pane -> pane.series }).containsExactly(
      listOf(SeriesKey(1)),
      listOf(SeriesKey(2), SeriesKey(3)),
      listOf(SeriesKey(4)),
    ).inOrder()
  }
}
