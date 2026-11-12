package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class SeriesGroupsTest {

  private fun s(column: Int, short: String, canonical: String) = DataLogSeries(
    column = column,
    name = short,
    short_name = short,
    kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
    canonical_id = canonical,
  )

  @Test
  fun cylinderFamiliesBundleUnderOneRowInCatalogueOrder() {
    val rpm = s(1, "E1 RPM", "engine[1].rpm")
    val egt = (1..4).map { s(10 + it, "E1 EGT$it", "engine[1].egt[$it]") }
    val cht = (1..4).map { s(20 + it, "E1 CHT$it", "engine[1].cht[$it]") }
    val ias = s(30, "IAS", "flight.ias")

    val groups = (listOf(rpm) + egt + cht + ias).seriesGroups()

    assertThat(groups.map { it.label }).containsExactly("RPM", "EGT", "CHT", "IAS")
      .inOrder()
    assertThat(groups[1].keys).containsExactly(
      SeriesKey(11), SeriesKey(12), SeriesKey(13), SeriesKey(14)
    ).inOrder()
    assertThat(groups[1].isBundle).isTrue()
    assertThat(groups[0].isBundle).isFalse()
  }

  @Test
  fun aTwinKeepsEachEnginesBundleApartAndNamesTheEngine() {
    val series = (1..2).flatMap { e ->
      (1..2).map { c -> s(e * 10 + c, "E$e EGT$c", "engine[$e].egt[$c]") }
    }
    assertThat(series.seriesGroups().map { it.label })
      .containsExactly("E1 EGT", "E2 EGT")
      .inOrder()
  }

  @Test
  fun aLoneCylinderAndUnmappedColumnsStayPlainRows() {
    val tit = s(1, "E1 TIT1", "engine[1].tit[1]")
    val unknown = s(2, "Foo", "")
    assertThat(listOf(tit, unknown).seriesGroups().map { it.label })
      .containsExactly("TIT1", "Foo")
      .inOrder()
  }
}
