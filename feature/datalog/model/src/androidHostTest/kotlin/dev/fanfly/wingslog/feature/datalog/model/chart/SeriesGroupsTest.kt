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
    assertThat(groups.map { it.title }).containsExactly("RPM", "EGT (4)", "CHT (4)", "Indicated Airspeed")
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

  @Test
  fun expandedTitlesSpellOutAbbreviationsOnSingleEngine() {
    val series = listOf(
      s(1, "Oil Press", "engine[1].oil_press"),
      s(2, "Oil Temp", "engine[1].oil_temp"),
      s(3, "Manifold Press", "engine[1].map"),
      s(4, "Fuel Press", "engine[1].fuel_press"),
      s(5, "Coolant Temp", ""),
      s(6, "Fuel Qty L", "fuel.qty[1]"),
      s(7, "E1 OilP", "engine[1].oil_press"),
      s(8, "OILP", "engine[1].oil_press"),
      s(9, "GndSpd", "flight.ground_speed"),
      s(10, "AltB", "flight.alt_baro"),
    )

    val groups = series.seriesGroups()

    assertThat(groups.map { it.title }).containsExactly(
      "Oil Pressure",
      "Oil Temperature",
      "Manifold Pressure",
      "Fuel Pressure",
      "Coolant Temperature",
      "Fuel Quantity (Left)",
      "Oil Pressure",
      "Oil Pressure",
      "Ground Speed",
      "Barometric Altitude",
    ).inOrder()

    // Chip labels remain compact
    assertThat(groups.map { it.label }).containsExactly(
      "Oil Press",
      "Oil Temp",
      "Manifold Press",
      "Fuel Press",
      "Coolant Temp",
      "Fuel Qty L",
      "OilP",
      "OILP",
      "GndSpd",
      "AltB",
    ).inOrder()
  }

  @Test
  fun aTwinPrefixesEngineOnExpandedTitles() {
    val series = listOf(
      s(1, "E1 Oil Press", "engine[1].oil_press"),
      s(2, "E2 Oil Press", "engine[2].oil_press"),
      s(3, "E2 MAP", "engine[2].map"),
    )

    val groups = series.seriesGroups()

    assertThat(groups.map { it.title }).containsExactly(
      "E1 Oil Pressure",
      "E2 Oil Pressure",
      "E2 Manifold Pressure",
    ).inOrder()
  }
}
