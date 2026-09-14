package dev.fanfly.wingslog.feature.datalog.model

/**
 * The parsed columns of one data log, keyed by source column index (`DataLogSeries.column`).
 *
 * Transient: built by the parser during import and again when a viewer opens, never persisted
 * (design §1.1 shape 2). Columnar so a pane can hand a series straight to a decimator.
 */
class DataLogSeriesData(
  /** Seconds elapsed since row 0, one per row. */
  val timeSeconds: IntArray,
  /** NUMERIC and DISCRETE series. */
  val numeric: Map<Int, NumericColumn>,
  /** TEXT series; null for an empty cell. */
  val text: Map<Int, Array<String?>>,
  /** The POSITION pseudo-series, or null when the log carries no latitude and longitude. */
  val position: PositionColumn?,
) {
  val rowCount: Int get() = timeSeconds.size
}

/** One numeric column: [raw] keeps `NaN` for empty cells; [filled] forward-fills them for drawing. */
class NumericColumn(val raw: FloatArray, val filled: FloatArray)

/** Latitude and longitude in degrees, `NaN` where the row had no fix. */
class PositionColumn(val latitude: DoubleArray, val longitude: DoubleArray)
