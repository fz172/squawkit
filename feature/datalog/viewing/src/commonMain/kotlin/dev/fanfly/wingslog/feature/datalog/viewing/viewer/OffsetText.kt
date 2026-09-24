package dev.fanfly.wingslog.feature.datalog.viewing.viewer

/** `UTC-07:00` from the record's offset minutes. */
internal fun offsetText(minutes: Int): String {
  val sign = if (minutes < 0) "-" else "+"
  val abs = kotlin.math.abs(minutes)
  return "UTC$sign${
    (abs / 60).toString()
      .padStart(2, '0')
  }:${
    (abs % 60).toString()
      .padStart(2, '0')
  }"
}
