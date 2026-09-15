package dev.fanfly.wingslog.feature.datalog.datamanager.csv

/**
 * The row-walking primitives both recorder parsers share.
 *
 * Every one of these indexes into the line and reads numbers in place rather than splitting cells
 * into strings. That is the difference between a phone parsing a 120,000-row file in a second and
 * in a minute, and it is why they are worth having in one place rather than one copy per format.
 */
internal class LineCursor(private val text: String, start: Int = 0) {

  var position: Int = start
    private set

  fun next(): String? {
    if (position >= text.length) return null
    var end = text.indexOf('\n', position)
    if (end < 0) end = text.length
    var stop = end
    if (stop > position && text[stop - 1] == '\r') stop--
    val line = text.substring(position, stop)
    position = end + 1
    return line
  }

  fun remainingLineCount(): Int {
    var count = 0
    var i = position
    while (i < text.length) {
      if (text[i] == '\n') count++
      i++
    }
    return count + 1
  }
}

/** One column while rows stream past: numbers and text both land here until the kind is known. */
internal class ColumnAccumulator(private val capacity: Int) {
  var floats: FloatArray? = null
  var texts: Array<String?>? = null
  var numericCount = 0
  var textCount = 0
  var min = Float.POSITIVE_INFINITY
  var max = Float.NEGATIVE_INFINITY
  private var lastText: String? = null

  fun number(row: Int, v: Float) {
    val f = floats ?: FloatArray(capacity) { Float.NaN }.also { floats = it }
    f[row] = v
    numericCount++
    if (v < min) min = v
    if (v > max) max = v
  }

  fun text(row: Int, v: String) {
    val t = texts ?: arrayOfNulls<String>(capacity).also { texts = it }
    // Statuses repeat for hundreds of rows; sharing the instance keeps a text column cheap.
    val shared = if (v == lastText) lastText!! else v.also { lastText = it }
    t[row] = shared
    textCount++
  }
}

/** "Oil Press (PSI)" → ("Oil Press", "PSI"); a name without parentheses keeps an empty unit. */
internal fun splitUnit(longName: String): Pair<String, String> {
  val open = longName.lastIndexOf('(')
  if (open <= 0 || !longName.endsWith(")")) return longName to ""
  return longName.substring(0, open)
    .trim() to longName.substring(open + 1, longName.length - 1)
    .trim()
}

internal fun parseIntAt(s: String, start: Int, end: Int): Int {
  var v = 0
  for (i in start until end) {
    val c = s[i]
    if (c !in '0'..'9') return -1
    v = v * 10 + (c - '0')
  }
  return v
}

/** "-07:00" → -420; "+05:30" → 330; anything else → 0. */
internal fun parseOffsetMinutes(s: String, start: Int, end: Int): Int {
  if (end - start < 5) return 0
  val sign = when (s[start]) {
    '-' -> -1; '+' -> 1; else -> return 0
  }
  val colon = s.indexOf(':', start)
  if (colon < 0 || colon >= end) return 0
  val h = parseIntAt(s, start + 1, colon)
  val m = parseIntAt(s, colon + 1, end)
  if (h < 0 || m < 0) return 0
  return sign * (h * 60 + m)
}

/** Strict: optional sign, digits, optional fraction. Anything else is NaN, so "3D-" stays text. */
internal fun parseDoubleAt(s: String, start: Int, end: Int): Double {
  var i = start
  var negative = false
  when (s[i]) {
    '-' -> {
      negative = true; i++
    }; '+' -> i++
  }
  if (i >= end) return Double.NaN
  // One integer mantissa and one division keeps "114.1003005" exact; summing scaled digits does not.
  var mantissa = 0L
  var digits = 0
  var fraction = 0
  while (i < end && s[i] in '0'..'9') {
    mantissa = mantissa * 10 + (s[i] - '0'); i++; digits++
  }
  if (i < end && s[i] == '.') {
    i++
    while (i < end && s[i] in '0'..'9') {
      mantissa = mantissa * 10 + (s[i] - '0'); i++; digits++; fraction++
    }
  }
  if (i != end || digits == 0) return Double.NaN
  if (digits > 18) return s.substring(start, end)
    .toDoubleOrNull() ?: Double.NaN
  val value =
    if (fraction == 0) mantissa.toDouble() else mantissa / POWERS_OF_TEN[fraction]
  return if (negative) -value else value
}

private val POWERS_OF_TEN = DoubleArray(19).also {
  var p = 1.0
  for (i in it.indices) {
    it[i] = p; p *= 10.0
  }
}

/** The typical gap between rows, ignoring a clock that stands still or steps backwards. */
internal fun medianPositiveDelta(epoch: LongArray, rows: Int): Int {
  if (rows < 2) return 1
  val deltas = IntArray(rows - 1)
  var n = 0
  for (i in 1 until rows) {
    val d = epoch[i] - epoch[i - 1]
    if (d > 0 && d < Int.MAX_VALUE) deltas[n++] = d.toInt()
  }
  if (n == 0) return 1
  deltas.copyOf(n)
    .sorted()
    .let { return it[n / 2] }
}

/** A drawing copy where an empty cell carries the last real reading instead of a gap. */
internal fun forwardFilled(raw: FloatArray): FloatArray {
  val out = raw.copyOf()
  var last = Float.NaN
  for (i in out.indices) {
    if (out[i].isNaN()) out[i] = last else last = out[i]
  }
  return out
}
