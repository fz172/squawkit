package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import org.junit.Test

class DecimationTest {

  private val time = IntArray(100) { it }              // 0..99 s at 1 Hz
  private val values = FloatArray(100) { it.toFloat() } // v == t

  @Test
  fun eachPixelColumnHoldsTheMinAndMaxOfItsBucket() {
    val d = Decimation.decimate(values, time, window = null, widthPx = 10)
    // 99 s across 10 columns: column 0 covers t in [0, 9.9), i.e. samples 0..9.
    assertThat(d.minY[0]).isEqualTo(0f)
    assertThat(d.maxY[0]).isEqualTo(9f)
    assertThat(d.minY[9]).isEqualTo(90f)
    assertThat(d.maxY[9]).isEqualTo(99f)
  }

  @Test
  fun aWindowReDecimatesFromFullResolution() {
    val d = Decimation.decimate(values, time, ViewWindow(40, 50), widthPx = 5)
    assertThat(d.minY[0]).isEqualTo(40f)
    assertThat(d.maxY[0]).isEqualTo(41f)
    assertThat(d.maxY[4]).isEqualTo(50f)
  }

  @Test
  fun gapsAndEmptyCellsLeaveColumnsNaN() {
    val sparse =
      FloatArray(100) { if (it in 20..29) Float.NaN else it.toFloat() }
    val d = Decimation.decimate(sparse, time, window = null, widthPx = 10)
    assertThat(d.minY[2].isNaN()).isTrue()
    assertThat(d.maxY[2].isNaN()).isTrue()
    assertThat(d.minY[3]).isEqualTo(30f)
    // A recording gap: rows jump from 10 s to 90 s, so the middle columns stay empty.
    val gappy = intArrayOf(0, 5, 10, 90, 95, 99)
    val g = Decimation.decimate(FloatArray(6) { 1f }, gappy, null, widthPx = 10)
    assertThat(g.minY[5].isNaN()).isTrue()
    assertThat(g.minY[0]).isEqualTo(1f)
    assertThat(g.minY[9]).isEqualTo(1f)
  }

  @Test
  fun visibleRangeAndCursorIndexUseBinarySearch() {
    assertThat(
      Decimation.visibleIndexRange(
        time,
        ViewWindow(10, 20)
      )
    ).isEqualTo(IndexRange(10, 20))
    assertThat(Decimation.visibleIndexRange(time, null)).isEqualTo(
      IndexRange(
        0,
        99
      )
    )
    assertThat(
      Decimation.visibleIndexRange(
        time,
        ViewWindow(200, 300)
      ).isEmpty
    ).isTrue()
    assertThat(Decimation.indexAt(time, 41.4)).isEqualTo(41)
    assertThat(Decimation.indexAt(time, 41.6)).isEqualTo(42)
    assertThat(Decimation.indexAt(time, -5.0)).isEqualTo(0)
    assertThat(Decimation.indexAt(time, 500.0)).isEqualTo(99)
    assertThat(Decimation.indexAt(IntArray(0), 1.0)).isEqualTo(-1)
  }

  @Test
  fun degenerateWidthsAndEmptyLogsAreSafe() {
    assertThat(
      Decimation.decimate(
        values,
        time,
        null,
        widthPx = 0
      ).width
    ).isEqualTo(0)
    assertThat(
      Decimation.decimate(
        FloatArray(0),
        IntArray(0),
        null,
        widthPx = 4
      ).minY.all { it.isNaN() }).isTrue()
    val one =
      Decimation.decimate(floatArrayOf(7f), intArrayOf(0), null, widthPx = 3)
    assertThat(one.minY[0]).isEqualTo(7f)
  }
}
