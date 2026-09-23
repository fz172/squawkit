package dev.fanfly.wingslog.feature.datalog.viewing.list

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.NumericColumn
import org.junit.Test

class DataLogSketchTest {

  private fun series(
    column: Int,
    name: String,
    kind: DataLogSeriesKind,
    samples: Int,
    min: Double = 0.0,
    max: Double = 100.0,
    canonicalId: String = "",
  ) = DataLogSeries(
    column = column,
    name = name,
    kind = kind,
    sample_count = samples,
    min = min,
    max = max,
    canonical_id = canonicalId,
  )

  private fun column(vararg values: Float) =
    NumericColumn(raw = values, filled = values)

  private fun data(vararg columns: Pair<Int, NumericColumn>) =
    DataLogSeriesData(
      timeSeconds = IntArray(columns.first().second.filled.size) { it },
      numeric = columns.toMap(),
      text = emptyMap(),
      position = null,
    )

  @Test
  fun sketchesTheSeriesTheChartOpensWith() {
    // Engine 1 RPM is the chart's default, however much else was sampled.
    val record = DataLog(
      series = listOf(
        series(
          0,
          "GPS Time of Week",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 999
        ),
        series(
          1,
          "E1 RPM",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 500,
          canonicalId = "engine[1].rpm"
        ),
        series(
          2,
          "E1 OilP",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 400,
          canonicalId = "engine[1].oil_press"
        ),
      ),
    )
    val data =
      data(0 to column(1f, 2f), 1 to column(1f, 2f), 2 to column(1f, 2f))

    assertThat(sketchOf(record, data).series?.name).isEqualTo("E1 RPM")
  }

  @Test
  fun fallsBackToTheFirstPlottableSeriesLikeTheChart() {
    val record = DataLog(
      series = listOf(
        series(
          0,
          "Mode",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT,
          samples = 900
        ),
        series(
          1,
          "Oil Press",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 800
        ),
        series(
          2,
          "CHT",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 700
        ),
      ),
    )
    val data = data(1 to column(1f, 2f), 2 to column(1f, 2f))

    assertThat(sketchOf(record, data).series?.name).isEqualTo("Oil Press")
  }

  @Test
  fun normalisesToTheSeriesRangeAndDownsamples() {
    val values = FloatArray(1000) { it.toFloat() }
    val record = DataLog(
      series = listOf(
        series(
          0,
          "RPM",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 1000,
          min = 0.0,
          max = 999.0
        )
      ),
    )

    val sketch = sketchOf(record, data(0 to column(*values))).series!!

    assertThat(sketch.points).hasSize(SKETCH_POINTS)
    assertThat(sketch.points.first()).isEqualTo(0f)
    assertThat(sketch.points.last()).isEqualTo(1f)
  }

  @Test
  fun aFlatSeriesIsNotSketched() {
    val record = DataLog(
      series = listOf(
        series(
          0,
          "Flat",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 10,
          min = 5.0,
          max = 5.0
        )
      ),
    )

    assertThat(sketchOf(record, data(0 to column(5f, 5f))).series).isNull()
  }

  @Test
  fun rowsBeforeTheSensorCameUpStayGaps() {
    // Forward-filling leaves the leading cells NaN; they must reach the pane as NaN, not as 0.
    val record = DataLog(
      series = listOf(
        series(
          0,
          "RPM",
          DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC,
          samples = 2,
          min = 0.0,
          max = 10.0
        )
      ),
    )

    val sketch = sketchOf(record, data(0 to column(Float.NaN, Float.NaN, 5f, 10f))).series!!

    assertThat(sketch.points[0].isNaN()).isTrue()
    assertThat(sketch.points.last()).isEqualTo(1f)
  }
}
