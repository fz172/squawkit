package dev.fanfly.wingslog.core.ui.common

import kotlin.math.roundToInt

/**
 * Formats a Double to a String with exactly one decimal place.
 * E.g., 12.34 -> "12.3", 12.0 -> "12.0", 12 -> "12.0"
 */
fun Double.formatToOneDecimalPlace(): String {
  val rounded = (this * 10.0).roundToInt()
  val isNegative = rounded < 0
  val absRounded = kotlin.math.abs(rounded)
  val str = absRounded.toString()

  val formatted = if (str.length == 1) {
    "0.$str"
  } else {
    str.dropLast(1) + "." + str.last()
  }

  return if (isNegative) {
    "-$formatted"
  } else {
    formatted
  }
}
