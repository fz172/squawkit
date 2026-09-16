package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

enum class Axis { LEFT, RIGHT, NONE }

/** The series of a pane that share a [unit], and the axis they read on (PRD R22). */
data class UnitGroup(
  val unit: String,
  val series: List<SeriesKey>,
  val axis: Axis
)

/** A fitted Y scale. */
data class YRange(val min: Float, val max: Float) {
  val span: Float get() = max - min

  /** 0 at [min], 1 at [max]. */
  fun fraction(value: Float): Float =
    if (span == 0f) 0.5f else (value - min) / span
}

object UnitGroups {

  private const val PAD_FRACTION = 0.08f

  /**
   * Groups [series] by unit in pane order: the first unit reads on the left axis, the second on the
   * right, the rest draw on their own scale and read only in chips (design §11.2).
   */
  fun group(series: List<Pair<SeriesKey, String>>): List<UnitGroup> {
    val order = LinkedHashMap<String, MutableList<SeriesKey>>()
    series.forEach { (key, unit) -> order.getOrPut(unit) { mutableListOf() } += key }
    return order.entries.mapIndexed { index, (unit, keys) ->
      UnitGroup(
        unit, keys, when (index) {
          0 -> Axis.LEFT; 1 -> Axis.RIGHT; else -> Axis.NONE
        }
      )
    }
  }

  /**
   * `[min, max]` of the visible samples across [columns], padded by 8% each side. A flat series
   * gets a symmetric band around its value so a line never sits on the pane edge; no samples at
   * all yields `[0, 1]`.
   */
  fun fit(columns: List<DecimatedSeries>): YRange {
    var min = Float.POSITIVE_INFINITY
    var max = Float.NEGATIVE_INFINITY
    columns.forEach { c ->
      for (i in 0 until c.width) {
        val lo = c.minY[i]
        val hi = c.maxY[i]
        if (!lo.isNaN() && lo < min) min = lo
        if (!hi.isNaN() && hi > max) max = hi
      }
    }
    if (min > max) return YRange(0f, 1f)
    if (min == max) {
      val band = if (min == 0f) 1f else kotlin.math.abs(min) * PAD_FRACTION
      return YRange(min - band, max + band)
    }
    val pad = (max - min) * PAD_FRACTION
    return YRange(min - pad, max + pad)
  }

  /** Grid lines at quartiles of [range] (design §11.2). */
  fun gridValues(range: YRange): List<Float> =
    (0..4).map { range.min + range.span * it / 4f }
}
