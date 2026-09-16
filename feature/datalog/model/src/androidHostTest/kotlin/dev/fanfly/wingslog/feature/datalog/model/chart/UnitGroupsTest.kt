package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class UnitGroupsTest {

  @Test
  fun firstUnitLeftSecondRightRestUnaxed() {
    val groups = UnitGroups.group(
      listOf(
        SeriesKey(1) to "PSI",
        SeriesKey(2) to "deg F",
        SeriesKey(3) to "PSI",
        SeriesKey(4) to "gal"
      ),
    )
    assertThat(groups.map { it.unit }).containsExactly("PSI", "deg F", "gal")
      .inOrder()
    assertThat(groups[0].series).containsExactly(SeriesKey(1), SeriesKey(3))
      .inOrder()
    assertThat(groups.map { it.axis }).containsExactly(
      Axis.LEFT,
      Axis.RIGHT,
      Axis.NONE
    )
      .inOrder()
  }

  @Test
  fun fitPadsEightPercentAndHandlesFlatAndEmpty() {
    val d = DecimatedSeries(
      floatArrayOf(10f, Float.NaN, 20f),
      floatArrayOf(15f, Float.NaN, 30f)
    )
    val r = UnitGroups.fit(listOf(d))
    assertThat(r.min).isWithin(1e-4f)
      .of(10f - 1.6f)
    assertThat(r.max).isWithin(1e-4f)
      .of(30f + 1.6f)

    val flat = UnitGroups.fit(
      listOf(
        DecimatedSeries(
          floatArrayOf(50f),
          floatArrayOf(50f)
        )
      )
    )
    assertThat(flat.min).isLessThan(50f)
    assertThat(flat.max).isGreaterThan(50f)
    assertThat(flat.fraction(50f)).isWithin(1e-6f)
      .of(0.5f)

    assertThat(UnitGroups.fit(emptyList())).isEqualTo(YRange(0f, 1f))
    assertThat(
      UnitGroups.fit(
        listOf(
          DecimatedSeries(
            floatArrayOf(Float.NaN),
            floatArrayOf(Float.NaN)
          )
        )
      )
    ).isEqualTo(YRange(0f, 1f))
  }

  @Test
  fun gridSitsAtQuartiles() {
    assertThat(UnitGroups.gridValues(YRange(0f, 100f))).containsExactly(
      0f,
      25f,
      50f,
      75f,
      100f
    )
      .inOrder()
  }
}
