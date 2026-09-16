package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.feature.datalog.model.ViewWindow

/** The shared time axis: a step ladder chosen from the chart width (PRD R23a, design §11.2). */
object TimeTicks {

  /** Seconds between labels, smallest first. */
  val STEP_LADDER: IntArray =
    intArrayOf(5, 10, 30, 60, 120, 300, 600, 900, 1800, 3600)

  /** The smallest step whose labels sit at least [minLabelSpacingPx] apart across [widthPx]. */
  fun step(spanSeconds: Int, widthPx: Int, minLabelSpacingPx: Float): Int {
    if (widthPx <= 0 || spanSeconds <= 0) return STEP_LADDER.last()
    val pxPerSecond = widthPx.toFloat() / spanSeconds
    return STEP_LADDER.firstOrNull { it * pxPerSecond >= minLabelSpacingPx }
      ?: STEP_LADDER.last()
  }

  /** Tick times in seconds: every multiple of [step] inside the window, inclusive. */
  fun ticks(window: ViewWindow, step: Int): List<Int> {
    if (step <= 0 || window.lengthSeconds < 0) return emptyList()
    val first = ((window.startSeconds + step - 1) / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= window.endSeconds }
      .toList()
  }

  /**
   * Tick times aligned to the wall clock (PRD R32): every elapsed second inside the window at
   * which the clock, [originSecondsOfDay] at t = 0, reads a multiple of [step].
   */
  fun clockTicks(
    window: ViewWindow,
    step: Int,
    originSecondsOfDay: Int
  ): List<Int> {
    if (step <= 0 || window.lengthSeconds < 0) return emptyList()
    val phase = ((step - originSecondsOfDay % step) % step + step) % step
    val first =
      window.startSeconds + ((phase - window.startSeconds) % step + step) % step
    return generateSequence(first) { it + step }.takeWhile { it <= window.endSeconds }
      .toList()
  }

  /** `HH:MM` for whole-minute steps, `HH:MM:SS` below that, wrapping past midnight. */
  fun clockLabel(secondsOfDay: Int, step: Int = 60): String {
    val s =
      ((secondsOfDay % SECONDS_PER_DAY) + SECONDS_PER_DAY) % SECONDS_PER_DAY
    val hh = (s / 3600).toString()
      .padStart(2, '0')
    val mm = ((s % 3600) / 60).toString()
      .padStart(2, '0')
    if (step >= 60) return "$hh:$mm"
    return "$hh:$mm:${
      (s % 60).toString()
        .padStart(2, '0')
    }"
  }

  private const val SECONDS_PER_DAY = 86_400

  /** `mm:ss` under an hour, `h:mm:ss` from an hour up. */
  fun label(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    val mm = m.toString()
      .padStart(2, '0')
    val ss = sec.toString()
      .padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
  }

  /** X of [seconds] within [window] across [widthPx]. */
  fun xOf(seconds: Double, window: ViewWindow, widthPx: Int): Float =
    if (window.lengthSeconds <= 0) 0f
    else ((seconds - window.startSeconds) / window.lengthSeconds * widthPx).toFloat()

  /** The label centre for a tick, shifted inward so an edge label stays inside the chart (R23a). */
  fun labelCenterX(tickX: Float, labelWidthPx: Float, widthPx: Int): Float =
    tickX.coerceIn(
      labelWidthPx / 2f,
      (widthPx - labelWidthPx / 2f).coerceAtLeast(labelWidthPx / 2f)
    )
}
