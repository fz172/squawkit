package dev.fanfly.wingslog.feature.dashboard.host.overview

/**
 * What the reading dialog's field may hold: digits, and one decimal point where the meter takes a
 * fraction.
 *
 * Filtered as it is typed rather than rejected on save, because the keyboard is only a hint — a
 * hardware keyboard and a paste both get past it. A comma is read as the point it is on half the
 * world's number pads.
 */
internal fun filterMeterInput(text: String, decimal: Boolean): String {
  val digitsAndPoints = text.replace(',', '.')
    .filter { it.isDigit() || (decimal && it == '.') }
  val point = digitsAndPoints.indexOf('.')
  if (point < 0) return digitsAndPoints
  // Keep the first point and drop the rest, so "12.5.3" reads 12.53 rather than failing to parse.
  return digitsAndPoints.substring(0, point + 1) +
    digitsAndPoints.substring(point + 1)
      .replace(".", "")
}

/**
 * The reading [text] names, or null when it names none.
 *
 * Zero is none: a meter reading zero and a meter nobody has read are stored the same way, so a zero
 * saved here would show a dash where the user had just typed a number.
 */
internal fun parseMeterInput(text: String): Double? =
  text.toDoubleOrNull()
    ?.takeIf { it > 0.0 }
