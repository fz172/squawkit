package dev.fanfly.wingslog.feature.datalog.model.chart

import kotlin.math.roundToInt

/** Visual severity tone for a discrete or text swimlane span. */
enum class SpanTone { NEUTRAL, INFO, CAUTION, FAULT }

/**
 * One contiguous time span `[startSec, endSec]` where a discrete, text, or stepped-frequency
 * series holds a constant value.
 */
data class DiscreteSpan(
  val startSec: Int,
  val endSec: Int,
  val rawText: String,
  val pillLabel: String,
  val tokens: List<String>,
  val tone: SpanTone,
)

/**
 * Collapses columnar discrete/text samples into contiguous [DiscreteSpan]s for Gantt swimlanes.
 *
 * Empty, null, `"NONE"`, `"---"`, and (for boolean/discrete channels) `0` / `NaN` rows are treated
 * as inactive gaps so normal flight draws no visual clutter.
 */
object DiscreteSpans {

  private val FaultKeywords =
    listOf("FAULT", "FAIL", "SLIP", "FIRE", "OIL PRESS", "FUEL PRESS")
  private val CautionKeywords =
    listOf("BKUP", "BACKUP", "CHECK", "LOI", "NOSOLN", "WARN")

  fun fromText(
    values: Array<String?>,
    timeSeconds: IntArray,
    durationSeconds: Int,
  ): List<DiscreteSpan> {
    if (values.isEmpty() || timeSeconds.isEmpty()) return emptyList()
    return collapse(timeSeconds, durationSeconds) { i ->
      val raw = values[i]?.trim().orEmpty()
      if (raw.isEmpty() || raw.equals(
          "NONE",
          ignoreCase = true
        ) || raw == "---"
      ) null else raw
    }.map { (start, end, raw) ->
      val tokens = if (" / " in raw) {
        raw.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
      } else {
        listOf(raw)
      }
      val pillLabel =
        if (tokens.size > 1) "${tokens.first()} +${tokens.size - 1}" else tokens.first()
      DiscreteSpan(
        startSec = start,
        endSec = end,
        rawText = raw,
        pillLabel = pillLabel,
        tokens = tokens,
        tone = toneFor(raw),
      )
    }
  }

  fun fromDiscrete(
    values: FloatArray,
    timeSeconds: IntArray,
    durationSeconds: Int,
    activeLabel: String,
  ): List<DiscreteSpan> {
    if (values.isEmpty() || timeSeconds.isEmpty()) return emptyList()
    val tone =
      toneFor(activeLabel).let { if (it == SpanTone.NEUTRAL) SpanTone.INFO else it }
    return collapse(timeSeconds, durationSeconds) { i ->
      val v = values[i]
      if (v.isNaN() || v == 0f) null else activeLabel
    }.map { (start, end, label) ->
      DiscreteSpan(
        startSec = start,
        endSec = end,
        rawText = label,
        pillLabel = label,
        tokens = listOf(label),
        tone = tone,
      )
    }
  }

  fun fromSteppedNumeric(
    values: FloatArray,
    timeSeconds: IntArray,
    durationSeconds: Int,
    unit: String,
  ): List<DiscreteSpan> {
    if (values.isEmpty() || timeSeconds.isEmpty()) return emptyList()
    val isFreq = unit.equals("MHz", ignoreCase = true)
    return collapse(timeSeconds, durationSeconds) { i ->
      val v = values[i]
      if (v.isNaN()) null else if (isFreq) formatMHz(v) else formatCompact(v)
    }.map { (start, end, text) ->
      DiscreteSpan(
        startSec = start,
        endSec = end,
        rawText = text,
        pillLabel = text,
        tokens = listOf(text),
        tone = SpanTone.INFO,
      )
    }
  }

  private data class RawSpan(val start: Int, val end: Int, val value: String)

  private inline fun collapse(
    timeSeconds: IntArray,
    durationSeconds: Int,
    valueAt: (Int) -> String?,
  ): List<RawSpan> {
    val count = timeSeconds.size
    val out = ArrayList<RawSpan>()
    var activeVal: String? = null
    var activeStart = 0
    for (i in 0 until count) {
      val v = valueAt(i)
      if (v != activeVal) {
        if (activeVal != null) {
          out += RawSpan(activeStart, timeSeconds[i], activeVal)
        }
        activeVal = v
        activeStart = timeSeconds[i]
      }
    }
    if (activeVal != null) {
      val end = maxOf(durationSeconds, timeSeconds[count - 1])
      out += RawSpan(activeStart, end, activeVal)
    }
    return out
  }

  private fun toneFor(text: String): SpanTone {
    val upper = text.uppercase()
    if (FaultKeywords.any { it in upper }) return SpanTone.FAULT
    if (CautionKeywords.any { it in upper }) return SpanTone.CAUTION
    return SpanTone.NEUTRAL
  }

  private fun formatMHz(v: Float): String {
    val milli = (v * 1000f).roundToInt()
    val whole = milli / 1000
    val frac = (milli % 1000).toString().padStart(3, '0')
    return "$whole.$frac"
  }

  private fun formatCompact(v: Float): String {
    val rounded = v.roundToInt()
    return if (kotlin.math.abs(v - rounded) < 0.05f) rounded.toString()
    else ((v * 10f).roundToInt() / 10f).toString()
  }
}
