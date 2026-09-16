package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation.MIN_SPAN_SECONDS
import kotlin.math.roundToInt

/**
 * The three time gestures as pure functions over the view (PRD R23, design §11.4). `null` is the
 * whole log; every result is clamped to `[0, duration]` and to a span of at least [MIN_SPAN_SECONDS].
 */
object Navigation {

  const val MIN_SPAN_SECONDS = 5

  /** [view], or the whole log when null. */
  fun effective(view: ViewWindow?, durationSeconds: Int): ViewWindow =
    view ?: ViewWindow(0, durationSeconds)

  /**
   * Scales the span by `1 / factor` (so a pinch that doubled the finger distance halves the span)
   * keeping the time under [anchorFraction] of the width where it is. Full span returns null.
   */
  fun zoomAround(
    view: ViewWindow?,
    durationSeconds: Int,
    anchorFraction: Double,
    factor: Double
  ): ViewWindow? {
    if (durationSeconds <= MIN_SPAN_SECONDS || factor <= 0.0 || factor.isNaN()) return view
    val current = effective(view, durationSeconds)
    val anchorT = current.startSeconds + anchorFraction.coerceIn(
      0.0,
      1.0
    ) * current.lengthSeconds
    val span = (current.lengthSeconds / factor).roundToInt()
      .coerceIn(MIN_SPAN_SECONDS, durationSeconds)
    val start =
      (anchorT - anchorFraction.coerceIn(0.0, 1.0) * span).roundToInt()
    return clamp(start, span, durationSeconds)
  }

  /** Moves the view by [deltaSeconds]; a no-op at full zoom-out and at either end. */
  fun pan(
    view: ViewWindow?,
    durationSeconds: Int,
    deltaSeconds: Double
  ): ViewWindow? {
    if (view == null) return null
    val span = view.lengthSeconds
    val start = (view.startSeconds + deltaSeconds).roundToInt()
    return clamp(start, span, durationSeconds)
  }

  /**
   * Zooms to the span between two x positions inside the current view. A brush shorter than the
   * minimum expands around its centre. Returns the current view unchanged when the brush is empty.
   */
  fun brushToWindow(
    view: ViewWindow?,
    durationSeconds: Int,
    x0Px: Float,
    x1Px: Float,
    widthPx: Int
  ): ViewWindow? {
    if (widthPx <= 0 || x0Px == x1Px) return view
    val current = effective(view, durationSeconds)
    val lo = minOf(x0Px, x1Px).coerceIn(0f, widthPx.toFloat())
    val hi = maxOf(x0Px, x1Px).coerceIn(0f, widthPx.toFloat())
    val t0 = current.startSeconds + lo / widthPx * current.lengthSeconds
    val t1 = current.startSeconds + hi / widthPx * current.lengthSeconds
    var span = (t1 - t0).roundToInt()
    var start = t0.roundToInt()
    if (span < MIN_SPAN_SECONDS) {
      val centre = (t0 + t1) / 2
      span = MIN_SPAN_SECONDS
      start = (centre - span / 2.0).roundToInt()
    }
    return clamp(start, span.coerceAtMost(durationSeconds), durationSeconds)
  }

  private fun clamp(start: Int, span: Int, durationSeconds: Int): ViewWindow? {
    if (span >= durationSeconds) return null
    val s = start.coerceIn(0, durationSeconds - span)
    return ViewWindow(s, s + span)
  }
}
