package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class DefaultLayoutTest {

  private fun s(column: Int, kind: DataLogSeriesKind, canonical: String = "") =
    DataLogSeries(column = column, kind = kind, canonical_id = canonical)

  @Test
  fun rpmWinsWhenPresentOtherwiseTheFirstPlottableSeries() {
    val text = s(1, DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT)
    val ias = s(2, DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, "flight.ias")
    val rpm =
      s(9, DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, "engine[1].rpm")

    val withRpm = defaultLayout(listOf(text, ias, rpm))
    assertThat(withRpm.panes.single().series).containsExactly(SeriesKey(9))
    assertThat(withRpm.targetPane).isEqualTo(withRpm.panes.single().id)

    assertThat(
      defaultLayout(
        listOf(
          text,
          ias
        )
      ).panes.single().series
    ).containsExactly(SeriesKey(2))
    assertThat(defaultLayout(listOf(text))).isEqualTo(ChartLayout.EMPTY)
    assertThat(defaultLayout(emptyList())).isEqualTo(ChartLayout.EMPTY)
  }

  @Test
  fun chipLabelDropsE1PrefixWhenSingleEngineAndKeepsItOnMultiEngine() {
    val e1Rpm = DataLogSeries(
      column = 1,
      name = "RPM",
      short_name = "E1 RPM",
      kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
      canonical_id = "engine[1].rpm",
    )
    val e2Rpm = DataLogSeries(
      column = 2,
      name = "RPM",
      short_name = "E2 RPM",
      kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
      canonical_id = "engine[2].rpm",
    )
    val avidyneEgt1 = DataLogSeries(
      column = 3,
      name = "E1",
      short_name = "E1",
      kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
      canonical_id = "engine[1].egt[1]",
    )

    val singleEngine = listOf(e1Rpm, avidyneEgt1)
    assertThat(singleEngine.isMultiEngine()).isFalse()
    assertThat(e1Rpm.chipLabel(multiEngine = false)).isEqualTo("RPM")
    assertThat(avidyneEgt1.chipLabel(multiEngine = false)).isEqualTo("E1")

    val twinEngine = listOf(e1Rpm, e2Rpm)
    assertThat(twinEngine.isMultiEngine()).isTrue()
    assertThat(e1Rpm.chipLabel(multiEngine = true)).isEqualTo("E1 RPM")
    assertThat(e2Rpm.chipLabel(multiEngine = true)).isEqualTo("E2 RPM")
  }
}
