package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.feature.datalog.model.ViewWindow

/** Min and max per pixel column; `NaN` where no sample fell in the column (design §11.2). */
class DecimatedSeries(val minY: FloatArray, val maxY: FloatArray) {
  val width: Int get() = minY.size
}

/** `[first, last]` row indices whose time falls inside [window]; `last < first` when none do. */
data class IndexRange(val first: Int, val last: Int) {
  val isEmpty: Boolean get() = last < first
}

object Decimation {

  /** Binary searches the monotonic [timeSeconds] for the rows inside [window] (inclusive). */
  fun visibleIndexRange(timeSeconds: IntArray, window: ViewWindow?): IndexRange {
    if (timeSeconds.isEmpty()) return IndexRange(0, -1)
    if (window == null) return IndexRange(0, timeSeconds.lastIndex)
    val first = lowerBound(timeSeconds, window.startSeconds)
    val last = upperBound(timeSeconds, window.endSeconds) - 1
    return IndexRange(first, last)
  }

  /**
   * One min and one max per pixel column across [range], from the forward-filled [values]. Columns
   * are equal spans of *time* across [window] (or the whole log), so a gap in the recording shows
   * as empty columns rather than stretched samples. O(rows) with no allocation beyond the output.
   */
  fun decimate(
    values: FloatArray,
    timeSeconds: IntArray,
    window: ViewWindow?,
    widthPx: Int,
  ): DecimatedSeries {
    val minY = FloatArray(widthPx) { Float.NaN }
    val maxY = FloatArray(widthPx) { Float.NaN }
    if (widthPx <= 0 || timeSeconds.isEmpty()) return DecimatedSeries(minY, maxY)
    val start = window?.startSeconds ?: timeSeconds.first()
    val end = window?.endSeconds ?: timeSeconds.last()
    val span = (end - start).coerceAtLeast(1)
    val range = visibleIndexRange(timeSeconds, window)
    for (i in range.first..range.last) {
      val v = values[i]
      if (v.isNaN()) continue
      val column = (((timeSeconds[i] - start).toLong() * widthPx) / span).toInt().coerceIn(0, widthPx - 1)
      if (minY[column].isNaN() || v < minY[column]) minY[column] = v
      if (maxY[column].isNaN() || v > maxY[column]) maxY[column] = v
    }
    return DecimatedSeries(minY, maxY)
  }

  /** The row nearest [t] seconds, for the cursor's chip values (design §11.2). */
  fun indexAt(timeSeconds: IntArray, t: Double): Int {
    if (timeSeconds.isEmpty()) return -1
    val hi = lowerBound(timeSeconds, kotlin.math.ceil(t).toInt())
    if (hi >= timeSeconds.size) return timeSeconds.lastIndex
    if (hi == 0) return 0
    val lo = hi - 1
    return if (t - timeSeconds[lo] <= timeSeconds[hi] - t) lo else hi
  }

  /** First index with `timeSeconds[i] >= t`. */
  private fun lowerBound(timeSeconds: IntArray, t: Int): Int {
    var lo = 0
    var hi = timeSeconds.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (timeSeconds[mid] < t) lo = mid + 1 else hi = mid
    }
    return lo
  }

  /** First index with `timeSeconds[i] > t`. */
  private fun upperBound(timeSeconds: IntArray, t: Int): Int {
    var lo = 0
    var hi = timeSeconds.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (timeSeconds[mid] <= t) lo = mid + 1 else hi = mid
    }
    return lo
  }
}
